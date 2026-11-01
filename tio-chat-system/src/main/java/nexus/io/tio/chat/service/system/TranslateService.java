package nexus.io.tio.chat.service.system;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.common.SysConfigService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.HttpClients;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * 文本翻译：有道与腾讯云
 */
public class TranslateService {

  public String resolveProvider(String provider) {
    if (provider != null && !provider.isBlank()) {
      return provider.toLowerCase();
    }
    String conf = Aop.get(SysConfigService.class).get("translateDefault");
    return conf == null || conf.isBlank() ? "tencent" : conf.toLowerCase();
  }

  public String translate(String text, String provider, String source, String target) {
    if (text == null || text.trim().isEmpty()) {
      return "";
    }
    String src = source == null ? "auto" : source;
    if ("tencent".equals(provider)) {
      return tencent(text, src, target == null ? "zh" : target);
    }
    return youdao(text, src, target == null ? "zh-CHS" : target);
  }

  public List<String> segments(String text, String provider, String source, String target) {
    String out = translate(text, provider, source, target);
    List<String> result = new ArrayList<>();
    if (out == null || out.isEmpty()) {
      return result;
    }
    for (String part : out.split("(?<=[。！？.!?；;\n])")) {
      if (!part.trim().isEmpty()) {
        result.add(part.trim());
      }
    }
    if (result.isEmpty()) {
      result.add(out);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private String youdao(String text, String source, String target) {
    SysConfigService config = Aop.get(SysConfigService.class);
    String appKey = config.get("youdaoAppKey");
    String appSecret = config.get("youdaoAppSecret");
    if (blank(appKey) || blank(appSecret)) {
      throw ChatErrors.of("有道翻译密钥未配置");
    }
    long salt = System.currentTimeMillis();
    long curtime = salt / 1000;
    String input = text.length() <= 20 ? text : text.substring(0, 10) + text.length() + text.substring(text.length() - 10);
    String sign = sha256Hex(appKey + input + salt + curtime + appSecret);
    String body = "q=" + enc(text) + "&from=" + enc(source) + "&to=" + enc(target) + "&appKey=" + enc(appKey) + "&salt=" + salt
        + "&sign=" + sign + "&signType=v3&curtime=" + curtime;
    String resp = HttpClients.post("https://openapi.youdao.com/api", "application/x-www-form-urlencoded", body, null);
    Map<String, Object> json = JsonUtils.parseToMap(resp, String.class, Object.class);
    if (!"0".equals(String.valueOf(json.get("errorCode")))) {
      throw ChatErrors.of("有道翻译失败:" + json.get("errorCode"));
    }
    List<Object> translation = (List<Object>) json.get("translation");
    if (translation == null || translation.isEmpty()) {
      throw ChatErrors.of("有道翻译结果为空");
    }
    return String.valueOf(translation.get(0));
  }

  /**
   * 腾讯云 TC3-HMAC-SHA256 签名调用 TextTranslate
   */
  @SuppressWarnings("unchecked")
  private String tencent(String text, String source, String target) {
    SysConfigService config = Aop.get(SysConfigService.class);
    String secretId = config.get("tencentSecretId");
    String secretKey = config.get("tencentApiKey");
    if (blank(secretId) || blank(secretKey)) {
      throw ChatErrors.of("腾讯云翻译密钥未配置");
    }
    long timestamp = Instant.now().getEpochSecond();
    String date = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(timestamp));
    String payload = JsonUtils.toJson(Kv.by("SourceText", text).set("Source", source).set("Target", target).set("ProjectId", 0));
    String canonical = String.join("\n", "POST", "/", "", "content-type:application/json", "host:tmt.tencentcloudapi.com", "",
        "content-type;host", sha256Hex(payload));
    String scope = date + "/tmt/tc3_request";
    String toSign = String.join("\n", "TC3-HMAC-SHA256", String.valueOf(timestamp), scope, sha256Hex(canonical));
    byte[] kDate = hmac(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
    byte[] kService = hmac(kDate, "tmt");
    byte[] kSigning = hmac(kService, "tc3_request");
    String signature = HexFormat.of().formatHex(hmac(kSigning, toSign));
    String authorization = "TC3-HMAC-SHA256 Credential=" + secretId + "/" + scope
        + ", SignedHeaders=content-type;host, Signature=" + signature;
    Map<String, String> headers = Map.of("X-TC-Action", "TextTranslate", "X-TC-Version", "2018-03-21", "X-TC-Region",
        "ap-guangzhou", "X-TC-Timestamp", String.valueOf(timestamp), "Authorization", authorization);
    String resp = HttpClients.post("https://tmt.tencentcloudapi.com", "application/json", payload, headers);
    Map<String, Object> json = JsonUtils.parseToMap(resp, String.class, Object.class);
    Map<String, Object> response = (Map<String, Object>) json.get("Response");
    if (response == null) {
      throw ChatErrors.of("腾讯云翻译结果为空");
    }
    if (response.get("Error") instanceof Map) {
      throw ChatErrors.of("腾讯云翻译失败:" + ((Map<String, Object>) response.get("Error")).get("Message"));
    }
    Object targetText = response.get("TargetText");
    if (targetText == null || targetText.toString().isBlank()) {
      throw ChatErrors.of("腾讯云翻译结果为空");
    }
    return targetText.toString();
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String sha256Hex(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] hmac(byte[] key, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
