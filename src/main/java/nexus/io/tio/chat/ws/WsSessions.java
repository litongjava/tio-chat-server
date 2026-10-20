package nexus.io.tio.chat.ws;

import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.core.ChannelContext;

/**
 * WebSocket 连接上的会话信息
 */
public class WsSessions {
  public static final String ATTR_SESSION = "chat.ws.session";

  public static String deviceGroup(long uid, String clientId) {
    return "device:" + uid + ":" + clientId;
  }

  public static void bind(ChannelContext channelContext, LoginSession session) {
    channelContext.set(ATTR_SESSION, session);
  }

  public static LoginSession get(ChannelContext channelContext) {
    return (LoginSession) channelContext.get(ATTR_SESSION);
  }
}
