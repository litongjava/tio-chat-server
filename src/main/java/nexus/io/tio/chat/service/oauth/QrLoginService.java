package nexus.io.tio.chat.service.oauth;

import java.util.UUID;

import com.jfinal.kit.Kv;

import cn.hutool.crypto.SecureUtil;
import nexus.io.jfinal.aop.Aop;
import nexus.io.redis.Redis;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.consts.RedisKeys;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.model.oauth.QrCodeResp;
import nexus.io.tio.chat.model.oauth.QrCodeResult;
import nexus.io.tio.chat.model.oauth.QrCodeStatus;
import nexus.io.tio.chat.model.oauth.ScanResp;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * 扫码登录：PENDING 30 秒，SCANNED 60 秒，CONFIRMED 10 秒
 */
public class QrLoginService {
  public static final String PENDING = "PENDING";
  public static final String SCANNED = "SCANNED";
  public static final String CONFIRMED = "CONFIRMED";
  public static final String EXPIRED = "EXPIRED";

  public QrCodeResp generate(String ip) {
    String qrId = UUID.randomUUID().toString();
    String deviceHash = SecureUtil.sha256(ip + "|" + System.currentTimeMillis() + "|" + qrId);
    save(qrId, new QrCodeStatus(PENDING, deviceHash, null, null), 30);
    return new QrCodeResp(qrId, deviceHash, System.currentTimeMillis() + 30_000, ip);
  }

  public ScanResp scan(String qrId, String ip) {
    String lock = Redis.use().lock("qr:scan:" + qrId, 5, 3);
    try {
      QrCodeStatus status = get(qrId);
      if (status == null || !PENDING.equals(status.getStatus())) {
        throw ChatErrors.of(ChatCode.OPERATION_EX, "二维码已失效");
      }
      status.setStatus(SCANNED);
      save(qrId, status, 60);
      return new ScanResp(System.currentTimeMillis() + 60_000, ip, "PC", null);
    } finally {
      Redis.use().unlock("qr:scan:" + qrId, lock);
    }
  }

  public long confirm(String qrId, LoginSession session) {
    QrCodeStatus status = get(qrId);
    if (status == null || !SCANNED.equals(status.getStatus())) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "二维码状态异常");
    }
    save(qrId, new QrCodeStatus(CONFIRMED, status.getDeviceHash(), session.getUserId(), session.getUid()), 10);
    return System.currentTimeMillis() + 10_000;
  }

  public QrCodeResult query(String qrId, String deviceHash, String deviceType, String clientId, String ip) {
    QrCodeStatus status = get(qrId);
    if (status == null) {
      return new QrCodeResult(EXPIRED, null);
    }
    if (deviceHash == null || !deviceHash.equals(status.getDeviceHash())) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "设备状态异常");
    }
    if (CONFIRMED.equals(status.getStatus())) {
      Redis.call(jedis -> jedis.del(RedisKeys.QR_STATUS + qrId));
      Kv user = Aop.get(DefUserService.class).getById(status.getUserId());
      return new QrCodeResult(CONFIRMED, Aop.get(LoginService.class).issue(user, deviceType, clientId, ip));
    }
    return new QrCodeResult(status.getStatus(), null);
  }

  private void save(String qrId, QrCodeStatus status, int seconds) {
    String json = JsonUtils.toJson(status);
    Redis.call(jedis -> jedis.setex(RedisKeys.QR_STATUS + qrId, seconds, json));
  }

  private QrCodeStatus get(String qrId) {
    if (qrId == null) {
      return null;
    }
    String json = Redis.call(jedis -> jedis.get(RedisKeys.QR_STATUS + qrId));
    return json == null ? null : JsonUtils.parse(json, QrCodeStatus.class);
  }
}
