package nexus.io.tio.chat.service.oauth;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.jfinal.kit.Kv;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import nexus.io.jfinal.aop.Aop;
import nexus.io.redis.Redis;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.consts.RedisKeys;
import nexus.io.tio.chat.service.common.MailService;
import nexus.io.tio.chat.service.common.SysConfigService;

/**
 * 图形验证码、邮件验证码与短信验证码
 */
@Slf4j
public class CaptchaService {
  public static final String GRANT_CAPTCHA = "CAPTCHA";
  public static final String REGISTER_EMAIL = "REGISTER_EMAIL";
  public static final String EMAIL_EDIT = "EMAIL_EDIT";
  public static final String REGISTER_SMS = "REGISTER_SMS";
  public static final String MOBILE_LOGIN = "MOBILE_LOGIN";
  public static final String MOBILE_EDIT = "MOBILE_EDIT";

  private static final int EXPIRE_SECONDS = 5 * 60;

  public Kv createImage(String key) {
    if (key == null || key.isBlank()) {
      key = UUID.randomUUID().toString();
    }
    LineCaptcha captcha = CaptchaUtil.createLineCaptcha(130, 48, 4, 20);
    String cacheKey = cacheKey(key, GRANT_CAPTCHA);
    String code = captcha.getCode().toLowerCase();
    Redis.call(jedis -> jedis.setex(cacheKey, EXPIRE_SECONDS, code));
    return Kv.by("uuid", key).set("img", captcha.getImageBase64Data());
  }

  public void check(String key, String templateCode, String value) {
    if (value == null || value.isBlank()) {
      throw ChatErrors.of(ChatCode.VALID_EX, "请输入验证码");
    }
    String cacheKey = cacheKey(key, templateCode);
    String code = Redis.call(jedis -> jedis.get(cacheKey));
    if (code == null || code.isEmpty()) {
      throw ChatErrors.of(ChatCode.VALID_EX, "验证码已过期");
    }
    if (!code.equalsIgnoreCase(value.trim())) {
      throw ChatErrors.of(ChatCode.VALID_EX, "验证码不正确");
    }
    Redis.call(jedis -> jedis.del(cacheKey));
  }

  public long sendEmailCode(String email, String templateCode) {
    DefUserService defUserService = Aop.get(DefUserService.class);
    if (REGISTER_EMAIL.equals(templateCode) && defUserService.existsEmail(email)) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "该邮箱已经被注册");
    } else if (EMAIL_EDIT.equals(templateCode) && defUserService.existsEmail(email)) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "该邮箱已经被他人使用");
    }
    String cacheKey = cacheKey(email, templateCode);
    assertSendAllowed(cacheKey);
    String code = RandomUtil.randomNumbers(6);
    Redis.call(jedis -> jedis.setex(cacheKey, EXPIRE_SECONDS, code));
    log.info("email code key={}, code={}", cacheKey, code);

    SysConfigService configService = Aop.get(SysConfigService.class);
    Map<String, String> params = new HashMap<>();
    params.put("emailCode", code);
    params.put("systemName", configService.get("systemName", ""));
    params.put("expireMinutes", String.valueOf(EXPIRE_SECONDS / 60));
    params.put("adminEmail", configService.get("masterEmail", ""));
    params.put("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    Aop.get(MailService.class).sendByTemplate(templateCode, email, params);
    return Redis.call(jedis -> jedis.ttl(cacheKey));
  }

  public boolean sendSmsCode(String mobile, String templateCode) {
    DefUserService defUserService = Aop.get(DefUserService.class);
    boolean exists = defUserService.existsMobile(mobile);
    if (REGISTER_SMS.equals(templateCode) && exists) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "该手机号已经被注册");
    } else if (MOBILE_LOGIN.equals(templateCode) && !exists) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "该手机号尚未注册，请先注册后在登陆。");
    } else if (MOBILE_EDIT.equals(templateCode) && exists) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "该手机号已经被他人使用");
    }
    String cacheKey = cacheKey(mobile, templateCode);
    assertSendAllowed(cacheKey);
    String code = RandomUtil.randomNumbers(4);
    Redis.call(jedis -> jedis.setex(cacheKey, EXPIRE_SECONDS, code));
    // 短信通道按部署环境接入，这里记录验证码便于联调
    log.info("sms code key={}, code={}", cacheKey, code);
    return true;
  }

  /**
   * 同一目标 60 秒内只能发送一次，24 小时内超过 3 次后间隔 5 分钟
   */
  private void assertSendAllowed(String cacheKey) {
    String resendKey = cacheKey + ":resend_lock";
    String countKey = cacheKey + ":send_count";
    Long ttl = Redis.call(jedis -> jedis.ttl(resendKey));
    if (ttl != null && ttl > 0) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "请" + ttl + "秒后再试");
    }
    long count = Redis.call(jedis -> {
      long value = jedis.incr(countKey);
      if (value == 1) {
        jedis.expire(countKey, 24 * 3600);
      }
      return value;
    });
    int interval = count <= 3 ? 60 : 300;
    Redis.call(jedis -> jedis.setex(resendKey, interval, "1"));
  }

  public static String cacheKey(String key, String templateCode) {
    return RedisKeys.CAPTCHA + templateCode + ":" + key;
  }
}
