package nexus.io.tio.chat.service.im.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.model.im.chat.ChatMessageReq;
import nexus.io.tio.chat.model.im.chat.MessageRecord;
import nexus.io.tio.chat.service.im.RoomQueryService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;

/**
 * 转发消息：type=1 逐条转发，其他为合并转发
 */
public class MessageForwardService {
  public static final int SINGLE = 1;

  public void forward(long uid, Long fromRoomId, Integer type, List<Long> roomIds, List<Long> messageIds) {
    RoomQueryService roomQueryService = Aop.get(RoomQueryService.class);
    ChatErrors.require(roomQueryService.getRoom(fromRoomId) != null, "房间不存在");
    ChatErrors.require(roomQueryService.isMember(fromRoomId, uid),
        roomQueryService.isGroup(roomQueryService.getRoom(fromRoomId)) ? "您不是群成员" : "你们不是好友关系");
    ChatErrors.require(messageIds != null && !messageIds.isEmpty(), "消息不能为空");
    List<MessageRecord> messages = new ArrayList<>();
    String sql = "select * from im_message where id in (" + Values.placeholders(messageIds.size()) + ") order by id";
    for (Kv row : Db.findMaps(sql, messageIds.toArray())) {
      messages.add(MessageRecord.from(row));
    }
    MessageService messageService = Aop.get(MessageService.class);
    for (Long roomId : roomIds) {
      if (type != null && type == SINGLE) {
        for (MessageRecord message : messages) {
          Object body = body(message);
          if (body == null) {
            continue;
          }
          ChatMessageReq req = ChatMessageReq.of(roomId, message.getType(), body);
          req.setSkip(true);
          messageService.send(req, uid);
        }
      } else {
        List<Kv> items = new ArrayList<>();
        for (MessageRecord message : messages) {
          items.add(Kv.by("uid", String.valueOf(message.getFromUid())).set("messageId", String.valueOf(message.getId())));
        }
        ChatMessageReq req = ChatMessageReq.of(roomId, MsgType.MERGE, Kv.by("body", items));
        req.setSkip(true);
        messageService.send(req, uid);
      }
    }
  }

  /**
   * 还原原消息的请求体，用于逐条转发
   */
  private Object body(MessageRecord message) {
    Map<String, Object> extra = message.getExtra();
    return switch (message.getType()) {
    case MsgType.TEXT -> {
      Map<String, Object> text = new LinkedHashMap<>();
      text.put("content", message.getContent());
      text.put("replyMsgId", message.getReplyMsgId());
      text.put("atUidList", extra.get("atUidList"));
      yield text;
    }
    case MsgType.IMG -> extra.get("imgMsgDTO");
    case MsgType.FILE -> extra.get("fileMsg");
    case MsgType.SOUND -> extra.get("soundMsgDTO");
    case MsgType.VIDEO -> extra.get("videoMsgDTO");
    case MsgType.EMOJI -> extra.get("emojisMsgDTO");
    case MsgType.MERGE -> extra.get("mergeMsgDTO");
    case MsgType.NOTICE -> extra.get("noticeMsgDTO");
    case MsgType.VIDEO_CALL -> extra.get("videoCallMsgDTO");
    case MsgType.AUDIO_CALL -> extra.get("audioCallMsgDTO");
    case MsgType.LOCATION -> extra.get("mapMsgDTO");
    default -> null;
    };
  }
}
