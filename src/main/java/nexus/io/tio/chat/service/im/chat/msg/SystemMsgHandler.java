package nexus.io.tio.chat.service.im.chat.msg;

import java.util.Map;

import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.model.im.chat.MessageRecord;

/**
 * 系统消息，请求体为字符串，统一放在 body.content 中传入
 */
public class SystemMsgHandler extends AbstractMsgHandler {

  @Override
  public int type() {
    return MsgType.SYSTEM;
  }

  @Override
  public void fill(MessageRecord message, Map<String, Object> body) {
    Object content = body.get("content");
    message.setContent(content == null ? null : content.toString());
  }

  @Override
  public Object show(MessageRecord message, Long receiveUid) {
    return message.getContent();
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
