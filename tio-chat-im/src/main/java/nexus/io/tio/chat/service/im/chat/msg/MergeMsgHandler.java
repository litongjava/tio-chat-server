package nexus.io.tio.chat.service.im.chat.msg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.model.im.chat.MessageRecord;
import nexus.io.tio.chat.utils.Values;

/**
 * 合并转发消息，content 保存前三条消息的摘要
 */
public class MergeMsgHandler extends AbstractMsgHandler {

  @Override
  public int type() {
    return MsgType.MERGE;
  }

  @Override
  public void fill(MessageRecord message, Map<String, Object> body) {
    List<Long> ids = new ArrayList<>();
    if (body.get("body") instanceof List) {
      for (Object item : (List<?>) body.get("body")) {
        if (item instanceof Map && ids.size() < 3) {
          Long id = Values.toLong(((Map<?, ?>) item).get("messageId"));
          if (id != null) {
            ids.add(id);
          }
        }
      }
    }
    List<String> content = new ArrayList<>();
    if (!ids.isEmpty()) {
      String sql = """
          select m.type, m.content, u.name from im_message m left join im_user u on u.id=m.from_uid
          where m.id in (%s) order by m.id
          """.formatted(Values.placeholders(ids.size()));
      for (Kv row : Db.findMaps(sql, ids.toArray())) {
        content.add(row.getStr("name") + ": " + summary(Values.toInt(row.get("type")), row.getStr("content")));
      }
    }
    Map<String, Object> stored = new LinkedHashMap<>(body);
    stored.remove("reply");
    stored.put("content", content);
    message.getExtra().put("mergeMsgDTO", stored);
    fillReply(message, body.get("replyMsgId"));
  }

  private String summary(Integer type, String content) {
    if (type == null) {
      return content;
    }
    return switch (type) {
    case MsgType.MERGE -> "[聊天记录]";
    case MsgType.SOUND -> "[语音]";
    case MsgType.VIDEO -> "[视频]";
    case MsgType.FILE -> "[文件]";
    case MsgType.IMG -> "[图片]";
    case MsgType.RECALL -> "[消息已撤回]";
    case MsgType.EMOJI -> "[表情]";
    case MsgType.NOTICE -> "[公告消息]";
    default -> content;
    };
  }

  @Override
  public Object show(MessageRecord message, Long receiveUid) {
    Map<String, Object> stored = message.extraMap("mergeMsgDTO");
    Map<String, Object> resp = stored == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stored);
    resp.put("reply", reply(message));
    return resp;
  }

  @Override
  public Object showReply(MessageRecord message) {
    return "合并消息";
  }

  @Override
  public String showContact(MessageRecord message) {
    return "[合并消息]";
  }
}
