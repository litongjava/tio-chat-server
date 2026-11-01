package nexus.io.tio.chat.model.im.chat;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话列表项
 */
@Data
@NoArgsConstructor
public class ChatRoomResp {
  private Long id;
  private Long detailId;
  private Long roomId;
  private Integer type;
  private Integer hotFlag;
  private String text;
  private String name;
  private String avatar;
  private LocalDateTime activeTime;
  private Integer unreadCount;
  private String account;
  private Boolean top;
  private Boolean hide;
  private Boolean shield;
  private Integer operate;
  private Integer muteNotification;
  private String remark;
  private String myName;
}
