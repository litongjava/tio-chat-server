package nexus.io.tio.chat.ws;

import lombok.extern.slf4j.Slf4j;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.ws.call.CallService;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.DefaultAioListener;

/**
 * 连接关闭时（主动关闭、心跳超时、网络中断）同步下线状态
 */
@Slf4j
public class ChatConnectionListener extends DefaultAioListener {

  @Override
  public void onBeforeClose(ChannelContext channelContext, Throwable throwable, String remark, boolean isRemove) {
    LoginSession session = WsSessions.get(channelContext);
    if (session == null) {
      return;
    }
    try {
      Aop.get(OnlineService.class).offline(session.getUid(), session.getClientId());
      Aop.get(CallService.class).leaveAll(session.getUid());
    } catch (Exception e) {
      log.error("sync offline failed, uid={}", session.getUid(), e);
    }
  }
}
