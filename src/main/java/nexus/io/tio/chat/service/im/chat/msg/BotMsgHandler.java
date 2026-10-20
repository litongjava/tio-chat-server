package nexus.io.tio.chat.service.im.chat.msg;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.model.im.chat.MessageRecord;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;

public class BotMsgHandler extends AbstractMsgHandler {

  @Override
  public int type() {
    return MsgType.BOT;
  }

  @Override
  public void check(Map<String, Object> body, long roomId, long uid) {
    ChatErrors.require(body.get("content") != null && !body.get("content").toString().isEmpty(), "内容不能为空");
    checkReply(body.get("replyMsgId"), roomId);
    checkSensitive(body.get("content"), "消息包含敏感词，请重新输入");
    if (body.get("atUidList") instanceof List) {
      List<Long> atUids = Values.toLongList((List<?>) body.get("atUidList"));
      List<Long> distinct = atUids.stream().distinct().filter(id -> id != 0L).toList();
      if (!distinct.isEmpty()) {
        Long count = Db.queryLong("select count(*) from im_user where id in (" + Values.placeholders(distinct.size()) + ")",
            distinct.toArray());
        ChatErrors.require(count != null && count == distinct.size(), "@用户不存在");
      }
    }
  }

  @Override
  public void fill(MessageRecord message, Map<String, Object> body) {
    message.setContent(filter(body.get("content")));
    fillReply(message, body.get("replyMsgId"));
    message.getExtra().put("urlContentMap", UrlTitleDiscover.discover(message.getContent()));
    if (body.get("atUidList") instanceof List && !((List<?>) body.get("atUidList")).isEmpty()) {
      message.getExtra().put("atUidList", body.get("atUidList"));
    }
  }

  @Override
  public Object show(MessageRecord message, Long receiveUid) {
    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("content", message.getContent());
    resp.put("urlContentMap", message.getExtra().get("urlContentMap"));
    resp.put("atUidList", atUidList(message));
    resp.put("reply", reply(message));
    return resp;
  }

  @Override
  public Object showReply(MessageRecord message) {
    return message.getContent();
  }

  @Override
  public String showContact(MessageRecord message) {
    return message.getContent();
  }
}
