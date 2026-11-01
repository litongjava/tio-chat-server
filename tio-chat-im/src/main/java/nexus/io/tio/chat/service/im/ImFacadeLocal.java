package nexus.io.tio.chat.service.im;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.facade.ImFacade;
import nexus.io.tio.chat.model.im.chat.ChatMessageReq;
import nexus.io.tio.chat.service.im.chat.ChatService;
import nexus.io.tio.chat.service.im.chat.MessageService;

/**
 * ImFacade 的本地实现
 */
public class ImFacadeLocal implements ImFacade {

  @Override
  public Long findUidByUserId(long userId, long tenantId) {
    return Aop.get(ImUserService.class).findUidByUserId(userId, tenantId);
  }

  @Override
  public boolean existsEmail(String email) {
    return Aop.get(ImUserService.class).existsEmail(email);
  }

  @Override
  public long register(long userId, String account, String name, String email, String avatar, Integer sex,
      long tenantId) {
    return Aop.get(ImUserService.class).register(userId, account, name, email, avatar, sex, tenantId);
  }

  @Override
  public void refreshLoginInfo(long uid, String ip) {
    Aop.get(ImUserService.class).refreshLoginInfo(uid, ip);
  }

  @Override
  public boolean isBlack(Long uid, String ip) {
    return Aop.get(BlackService.class).isBlack(uid, ip);
  }

  @Override
  public long sendMessage(ChatMessageReq req, long uid) {
    return Aop.get(MessageService.class).send(req, uid);
  }

  @Override
  public void ack(long uid, Long msgId) {
    Aop.get(ChatService.class).ack(uid, msgId);
  }
}
