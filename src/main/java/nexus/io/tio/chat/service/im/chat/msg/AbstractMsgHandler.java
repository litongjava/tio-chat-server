package nexus.io.tio.chat.service.im.chat.msg;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.model.im.chat.MessageRecord;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.service.im.SensitiveWordService;
import nexus.io.tio.chat.service.im.UserSummaryService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;

public abstract class AbstractMsgHandler implements MsgHandler {
  public static final int CAN_CALLBACK_GAP_COUNT = 100;

  /**
   * 回复消息的展示信息
   */
  protected Map<String, Object> reply(MessageRecord message) {
    if (message.getReplyMsgId() == null) {
      return null;
    }
    MessageRecord replied = MessageRecord.from(Db.findFirstMap("select * from im_message where id=?", message.getReplyMsgId()));
    if (replied == null || !Objects.equals(replied.getStatus(), MsgType.STATUS_NORMAL)) {
      return null;
    }
    Map<String, Object> reply = new LinkedHashMap<>();
    reply.put("id", String.valueOf(replied.getId()));
    reply.put("uid", String.valueOf(replied.getFromUid()));
    UserSummary user = Aop.get(UserSummaryService.class).get(replied.getFromUid());
    reply.put("username", user == null ? null : user.getName());
    reply.put("type", replied.getType());
    reply.put("body", MsgHandlers.get(replied.getType()).showReply(replied));
    Integer gapCount = message.getGapCount();
    reply.put("canCallback", gapCount != null && gapCount <= CAN_CALLBACK_GAP_COUNT ? 1 : 0);
    reply.put("gapCount", gapCount);
    return reply;
  }

  /**
   * 校验回复的消息在同一会话中
   */
  protected void checkReply(Object replyMsgId, long roomId) {
    Long replyId = Values.toLong(replyMsgId);
    if (replyId == null) {
      return;
    }
    Kv replied = Db.findFirstMap("select room_id from im_message where id=?", replyId);
    ChatErrors.require(replied != null, "回复消息不存在");
    ChatErrors.require(Objects.equals(replied.getLong("room_id"), roomId), "只能回复相同会话内的消息");
  }

  /**
   * 计算与被回复消息之间间隔的消息数
   */
  protected void fillReply(MessageRecord message, Object replyMsgId) {
    Long replyId = Values.toLong(replyMsgId);
    if (replyId == null) {
      return;
    }
    Long count = Db.queryLong("select count(*) from im_message where room_id=? and id>?", message.getRoomId(), replyId);
    message.setReplyMsgId(replyId);
    message.setGapCount(count == null ? 1 : count.intValue() + 1);
  }

  protected void checkSensitive(Object content, String message) {
    if (content != null) {
      ChatErrors.require(!Aop.get(SensitiveWordService.class).hasSensitiveWord(content.toString()), message);
    }
  }

  protected String filter(Object content) {
    return content == null ? null : Aop.get(SensitiveWordService.class).filter(content.toString());
  }

  protected List<Long> atUidList(MessageRecord message) {
    Object value = message.getExtra() == null ? null : message.getExtra().get("atUidList");
    return value instanceof List ? Values.toLongList((List<?>) value) : null;
  }

  /**
   * 文件类消息：把请求体整体保存在 extra 的指定字段中
   */
  protected void fillExtraBody(MessageRecord message, Map<String, Object> body, String key) {
    Map<String, Object> stored = new LinkedHashMap<>(body);
    stored.remove("reply");
    message.getExtra().put(key, stored);
    if (body.get("atUidList") instanceof List) {
      message.getExtra().put("atUidList", body.get("atUidList"));
    }
    fillReply(message, body.get("replyMsgId"));
  }

  /**
   * 展示 extra 中保存的请求体，并补充 @ 列表与回复信息
   */
  protected Object showExtraBody(MessageRecord message, String key, boolean withReply) {
    Map<String, Object> stored = message.extraMap(key);
    Map<String, Object> resp = stored == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stored);
    if (withReply) {
      resp.put("atUidList", atUidList(message));
      resp.put("reply", reply(message));
    }
    return resp;
  }
}
