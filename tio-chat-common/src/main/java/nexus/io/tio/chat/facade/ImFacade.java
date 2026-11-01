package nexus.io.tio.chat.facade;

import nexus.io.tio.chat.model.im.chat.ChatMessageReq;

/**
 * IM 服务（luohuo-im-server）对其他服务开放的能力：IM 用户、黑名单、消息发送与确认。
 * 同进程运行时直接调用本地实现，分服务部署时通过服务名远程调用。
 */
public interface ImFacade {

  Long findUidByUserId(long userId, long tenantId);

  boolean existsEmail(String email);

  long register(long userId, String account, String name, String email, String avatar, Integer sex, long tenantId);

  void refreshLoginInfo(long uid, String ip);

  boolean isBlack(Long uid, String ip);

  /**
   * 以 uid 身份发送消息，返回消息 id
   */
  long sendMessage(ChatMessageReq req, long uid);

  void ack(long uid, Long msgId);
}
