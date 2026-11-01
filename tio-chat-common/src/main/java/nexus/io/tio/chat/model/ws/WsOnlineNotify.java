package nexus.io.tio.chat.model.ws;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上下线通知，type=1 群成员，type=2 好友
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WsOnlineNotify {
  private String uid;
  private String clientId;
  private String roomId;
  private Long lastOptTime;
  private Long onlineNum;
  private Integer type;
}
