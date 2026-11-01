package nexus.io.tio.chat.model.im.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送消息请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageReq {
  private Long roomId;
  private Integer msgType;
  private Object body;
  /** 跳过成员与屏蔽校验，系统消息使用 */
  private boolean skip;
  /** 临时会话消息 */
  private boolean temp;
  private boolean pushMessage;

  public static ChatMessageReq of(Long roomId, Integer msgType, Object body) {
    ChatMessageReq req = new ChatMessageReq();
    req.setRoomId(roomId);
    req.setMsgType(msgType);
    req.setBody(body);
    return req;
  }
}
