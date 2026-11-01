package nexus.io.tio.chat.service.im.chat.msg;

import java.util.Map;

import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.model.im.chat.MessageRecord;

public class EmojisMsgHandler extends AbstractMsgHandler {

  @Override
  public int type() {
    return MsgType.EMOJI;
  }

  @Override
  public void check(Map<String, Object> body, long roomId, long uid) {
    checkReply(body.get("replyMsgId"), roomId);
  }

  @Override
  public void fill(MessageRecord message, Map<String, Object> body) {
    fillExtraBody(message, body, "emojisMsgDTO");
  }

  @Override
  public Object show(MessageRecord message, Long receiveUid) {
    return showExtraBody(message, "emojisMsgDTO", true);
  }

  @Override
  public Object showReply(MessageRecord message) {
    return "表情";
  }

  @Override
  public String showContact(MessageRecord message) {
    return "[动画表情]";
  }
}
