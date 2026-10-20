package nexus.io.tio.chat.service.im.chat.msg;

import java.util.LinkedHashMap;
import java.util.Map;

import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.model.im.chat.MessageRecord;

/**
 * 群公告消息
 */
public class NoticeMsgHandler extends AbstractMsgHandler {

  @Override
  public int type() {
    return MsgType.NOTICE;
  }

  @Override
  public void check(Map<String, Object> body, long roomId, long uid) {
    checkSensitive(body.get("content"), "消息包含敏感词，请重新输入");
  }

  @Override
  public void fill(MessageRecord message, Map<String, Object> body) {
    Map<String, Object> stored = new LinkedHashMap<>(body);
    stored.remove("reply");
    stored.put("content", filter(body.get("content")));
    message.getExtra().put("noticeMsgDTO", stored);
    message.getExtra().put("urlContentMap", UrlTitleDiscover.discover((String) stored.get("content")));
    fillReply(message, body.get("replyMsgId"));
  }

  @Override
  public Object show(MessageRecord message, Long receiveUid) {
    Map<String, Object> stored = message.extraMap("noticeMsgDTO");
    Map<String, Object> resp = stored == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stored);
    resp.put("reply", reply(message));
    return resp;
  }

  @Override
  public Object showReply(MessageRecord message) {
    return "公告";
  }

  @Override
  public String showContact(MessageRecord message) {
    return "[公告]";
  }
}
