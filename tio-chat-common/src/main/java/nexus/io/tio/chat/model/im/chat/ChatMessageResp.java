package nexus.io.tio.chat.model.im.chat;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息展示结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResp {
  private UserInfo fromUser;
  private Message message;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UserInfo {
    private String uid;
  }

  @Data
  @NoArgsConstructor
  public static class Message {
    private String id;
    private String roomId;
    private LocalDateTime sendTime;
    private Integer type;
    private Object body;
    private Map<Integer, MarkItem> messageMarks;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class MarkItem {
    private Integer count;
    private Boolean userMarked;
  }
}
