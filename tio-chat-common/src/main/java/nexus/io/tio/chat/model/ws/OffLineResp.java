package nexus.io.tio.chat.model.ws;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号在同类设备登录后，旧设备收到的下线通知
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OffLineResp {
  private Long uid;
  private String client;
  private String clientId;
  private String ip;
  private String uuid;
}
