package nexus.io.tio.chat.service.system;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.common.SysConfigService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * 客户端直传对象存储的凭证：七牛上传 token 或 MinIO/S3 预签名 PUT 地址
 */
public class StorageService {

  public Kv token(String scene, String fileName) {
    SysConfigService config = Aop.get(SysConfigService.class);
    String engine = config.get("storageDefault");
    engine = engine == null || engine.isBlank() ? "qiniu" : engine;
    switch (engine) {
    case "qiniu":
      return qiniu(config);
    case "minio":
      String safeScene = scene == null || scene.isBlank() ? "chat" : scene;
      String safeName = fileName == null || fileName.isBlank() ? "file" : fileName;
      return minio(config, safeScene + "/" + System.currentTimeMillis() + "_" + safeName);
    default:
      throw ChatErrors.of("不支持的存储引擎: " + engine);
    }
  }

  /**
   * 七牛上传凭证：AccessKey:urlsafe_base64(hmac_sha1(policy)):urlsafe_base64(policy)
   */
  private Kv qiniu(SysConfigService config) {
    String accessKey = config.get("qnAccessKey", "");
    String secretKey = config.get("qnSecretKey", "");
    String bucket = config.get("qnStorageName", "");
    long deadline = System.currentTimeMillis() / 1000 + 3600;
    String policy = JsonUtils.toJson(Kv.by("scope", bucket).set("deadline", deadline));
    Base64.Encoder encoder = Base64.getUrlEncoder();
    String encodedPolicy = encoder.encodeToString(policy.getBytes(StandardCharsets.UTF_8));
    String sign = encoder.encodeToString(hmac("HmacSHA1", secretKey.getBytes(StandardCharsets.UTF_8), encodedPolicy));
    String token = accessKey + ":" + sign + ":" + encodedPolicy;
    return Kv.by("domain", config.get("qnUploadUrl", "")).set("token", token).set("storagePrefix", config.get("qnStorageName"));
  }

  /**
   * S3 SigV4 查询串签名的预签名 PUT 地址，有效期 1 小时
   */
  private Kv minio(SysConfigService config, String objectKey) {
    String endpoint = config.get("minioEndpoint", "");
    String accessKey = config.get("minioAccessKey", "");
    String secretKey = config.get("minioSecretKey", "");
    String bucket = config.get("minioBucket", "");
    String urlPrefix = config.get("minioUrlPrefix", "");
    String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    String host = base.replaceFirst("^https?://", "");
    String region = "us-east-1";
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
    String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String scope = date + "/" + region + "/s3/aws4_request";
    String path = "/" + bucket + "/" + encodePath(objectKey);
    String query = "X-Amz-Algorithm=AWS4-HMAC-SHA256" + "&X-Amz-Credential=" + encode(accessKey + "/" + scope)
        + "&X-Amz-Date=" + amzDate + "&X-Amz-Expires=3600" + "&X-Amz-SignedHeaders=host";
    String canonicalRequest = "PUT\n" + path + "\n" + query + "\nhost:" + host + "\n\nhost\nUNSIGNED-PAYLOAD";
    String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest);
    byte[] kDate = hmac("HmacSHA256", ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date);
    byte[] kRegion = hmac("HmacSHA256", kDate, region);
    byte[] kService = hmac("HmacSHA256", kRegion, "s3");
    byte[] kSigning = hmac("HmacSHA256", kService, "aws4_request");
    String signature = HexFormat.of().formatHex(hmac("HmacSHA256", kSigning, stringToSign));
    String uploadUrl = base + path + "?" + query + "&X-Amz-Signature=" + signature;
    String downloadUrl;
    if (urlPrefix != null && !urlPrefix.isBlank()) {
      downloadUrl = urlPrefix.endsWith("/") ? urlPrefix + objectKey : urlPrefix + "/" + objectKey;
    } else {
      downloadUrl = base + "/" + bucket + "/" + objectKey;
    }
    return Kv.by("uploadUrl", uploadUrl).set("downloadUrl", downloadUrl).set("objectKey", objectKey);
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
  }

  private static String encodePath(String path) {
    StringBuilder sb = new StringBuilder();
    for (String part : path.split("/")) {
      if (sb.length() > 0) {
        sb.append('/');
      }
      sb.append(encode(part));
    }
    return sb.toString();
  }

  private static byte[] hmac(String algorithm, byte[] key, String data) {
    try {
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(key, algorithm));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String sha256Hex(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
