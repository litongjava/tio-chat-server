package nexus.io.tio.chat.service.im.chat.msg;

import java.util.Map;

import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.model.im.chat.MessageRecord;

public class FileMsgHandler extends AbstractMsgHandler {

  @Override
  public int type() {
    return MsgType.FILE;
  }

  @Override
  public void check(Map<String, Object> body, long roomId, long uid) {
    checkReply(body.get("replyMsgId"), roomId);
  }

  @Override
  public void fill(MessageRecord message, Map<String, Object> body) {
    fillExtraBody(message, body, "fileMsg");
  }

  @Override
  public Object show(MessageRecord message, Long receiveUid) {
    return showExtraBody(message, "fileMsg", true);
  }

  @Override
  public Object showReply(MessageRecord message) {
    return "文件:" + fileName(message);
  }

  @Override
  public String showContact(MessageRecord message) {
    return "[文件]" + fileName(message);
  }

  private String fileName(MessageRecord message) {
    Map<String, Object> file = message.extraMap("fileMsg");
    Object name = file == null ? null : file.get("fileName");
    return name == null ? "" : name.toString();
  }
}
