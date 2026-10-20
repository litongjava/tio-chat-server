package nexus.io.tio.chat.service.im.chat.msg;

import java.util.Map;

import nexus.io.tio.chat.model.im.chat.MessageRecord;

/**
 * 一种消息类型的校验、保存与展示
 */
public interface MsgHandler {

  int type();

  /**
   * 发送前校验
   */
  default void check(Map<String, Object> body, long roomId, long uid) {
  }

  /**
   * 把请求体写入待保存的消息（content、replyMsgId、gapCount、extra）
   */
  void fill(MessageRecord message, Map<String, Object> body);

  Object show(MessageRecord message, Long receiveUid);

  Object showReply(MessageRecord message);

  String showContact(MessageRecord message);
}
