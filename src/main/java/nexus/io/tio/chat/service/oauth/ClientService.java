package nexus.io.tio.chat.service.oauth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.utils.ChatErrors;

/**
 * 校验客户端凭证：Authorization 头为 base64(clientId:clientSecret)，允许带 Basic 前缀
 */
public class ClientService {

  public void checkAuthorization(String header) {
    if (header == null || header.isBlank()) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "请在.env文件中配置正确的客户端ID或者客户端秘钥");
    }
    String value = header.trim();
    if (value.regionMatches(true, 0, "Basic ", 0, 6)) {
      value = value.substring(6).trim();
    }
    String decoded;
    try {
      decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "客户端凭证格式错误");
    }
    int index = decoded.indexOf(':');
    if (index <= 0) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "客户端凭证格式错误");
    }
    String clientId = decoded.substring(0, index);
    String clientSecret = decoded.substring(index + 1);
    String sql = """
        select client_id, state from def_client
        where client_id=? and client_secret=? and is_del=0 limit 1
        """;
    Kv client = Db.findFirstMap(sql, clientId, clientSecret);
    if (client == null) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "请在.env文件中配置正确的客户端ID或者客户端秘钥");
    }
    Object state = client.get("state");
    if (state != null && !Boolean.TRUE.equals(state) && !"1".equals(state.toString())) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "客户端[" + clientId + "]已被禁用");
    }
  }
}
