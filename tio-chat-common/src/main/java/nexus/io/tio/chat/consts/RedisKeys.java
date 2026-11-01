package nexus.io.tio.chat.consts;

/**
 * Redis key 约定，与 原服务 保持一致
 */
public interface RedisKeys {
  String TOKEN_SESSION = "Token:login:token:";
  String USER_TOKENS = "Token:login:session:";
  String REFRESH_TOKEN = "Token:temp-token:";
  String CAPTCHA = "luohuo:captcha:";
  String EMAIL_CODE = "luohuo:emailCode:";
  String QR_STATUS = "luohuo:qr_status:";
  String GLOBAL_USERS_ONLINE = "luohuo:global_users_online";
  String GLOBAL_DEVICES_ONLINE = "luohuo:global_devices_online";
  String PASSAGE_MSG = "luohuo:passage_msg:";
}
