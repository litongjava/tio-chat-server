package nexus.io.tio.chat.service.im.chat.msg;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.model.im.chat.MessageRecord;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.service.im.UserSummaryService;
import nexus.io.tio.chat.utils.Values;

/**
 * 音视频通话记录消息，展示文案随查看者变化
 */
public class CallMsgHandler extends AbstractMsgHandler {
  private final boolean video;

  public CallMsgHandler(boolean video) {
    this.video = video;
  }

  private String key() {
    return video ? "videoCallMsgDTO" : "audioCallMsgDTO";
  }

  @Override
  public int type() {
    return video ? MsgType.VIDEO_CALL : MsgType.AUDIO_CALL;
  }

  @Override
  public void fill(MessageRecord message, Map<String, Object> body) {
    message.getExtra().put(key(), new LinkedHashMap<>(body));
  }

  @Override
  public Object show(MessageRecord message, Long receiveUid) {
    Map<String, Object> dto = message.extraMap(key());
    if (dto == null) {
      return "通话状态未知";
    }
    if (video && Values.isTrue(dto.get("isGroup"))) {
      if (Values.isTrue(dto.get("begin"))) {
        UserSummary user = Aop.get(UserSummaryService.class).get(message.getFromUid());
        return (user == null ? "" : user.getName()) + "发起了视频通话";
      }
      return "视频通话已结束";
    }
    boolean isSender = Objects.equals(receiveUid, message.getFromUid());
    boolean isCreator = Objects.equals(receiveUid, Values.toLong(dto.get("creator")));
    String state = dto.get("state") == null ? null : dto.get("state").toString();
    if (state == null) {
      return "通话状态未知";
    }
    boolean self = video ? !isSender : isSender == isCreator;
    return switch (state) {
    case "REJECTED" -> video ? (self ? "已拒绝" : "对方已拒绝") : (self ? "已拒绝" : "对方已拒绝");
    case "TIMEOUT" -> isSender ? "对方未接听" : "未接听";
    case "CANCEL" -> video ? (isSender ? "已取消" : "对方已取消") : (self ? "已取消" : "对方已取消");
    case "ONGOING" -> "通话进行中";
    case "FAILED" -> "连接失败";
    case "RINGING" -> video ? "房间响铃中" : "对方响铃中";
    default -> "通话时长 " + formatDuration(Values.toLong(dto.get("duration")));
    };
  }

  private String formatDuration(Long seconds) {
    if (seconds == null || seconds < 0) {
      return "00:00";
    }
    return String.format("%d:%02d", seconds / 60, seconds % 60);
  }

  @Override
  public Object showReply(MessageRecord message) {
    return video ? "[视频通话]" : "[语音通话]";
  }

  @Override
  public String showContact(MessageRecord message) {
    return video ? "[视频通话]" : "[语音通话]";
  }
}
