package nexus.io.tio.chat.ws;

import lombok.extern.slf4j.Slf4j;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.service.oauth.TokenService;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.Tio;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.websocket.common.WebSocketRequest;
import nexus.io.tio.websocket.server.handler.IWebSocketHandler;

/**
 * 客户端连接地址：/api/ws/ws?clientId=xxx&Token=xxx
 */
@Slf4j
public class ChatWebSocketHandler implements IWebSocketHandler {

  @Override
  public HttpResponse handshake(HttpRequest request, HttpResponse response, ChannelContext channelContext)
      throws Exception {
    String token = request.getParam("Token");
    if (token == null || token.isBlank()) {
      token = request.getParam("token");
    }
    String clientId = request.getParam("clientId");
    LoginSession session = Aop.get(TokenService.class).getSession(token);
    if (session == null || session.getUid() == null || clientId == null || clientId.isBlank()) {
      log.info("reject ws handshake, clientId={}", clientId);
      return null;
    }
    LoginSession wsSession = LoginSession.builder().token(token).uid(session.getUid()).userId(session.getUserId())
        .tenantId(session.getTenantId()).systemType(session.getSystemType()).deviceType(session.getDeviceType())
        .clientId(clientId).loginTime(session.getLoginTime()).build();
    WsSessions.bind(channelContext, wsSession);
    Tio.bindUserId(channelContext, String.valueOf(session.getUid()));
    Tio.bindGroup(channelContext, WsSessions.deviceGroup(session.getUid(), clientId));
    Tio.bindToken(channelContext, token);
    return response;
  }

  @Override
  public void onAfterHandshaked(HttpRequest request, HttpResponse response, ChannelContext channelContext)
      throws Exception {
    LoginSession session = WsSessions.get(channelContext);
    Aop.get(OnlineService.class).online(session.getUid(), session.getClientId());
  }

  @Override
  public Object onText(WebSocketRequest wsRequest, String text, ChannelContext channelContext) throws Exception {
    LoginSession session = WsSessions.get(channelContext);
    if (session == null) {
      return null;
    }
    Aop.get(WsMessageDispatcher.class).dispatch(channelContext, session, text);
    return null;
  }

  @Override
  public Object onBytes(WebSocketRequest wsRequest, byte[] bytes, ChannelContext channelContext) throws Exception {
    return null;
  }

  @Override
  public Object onClose(WebSocketRequest wsRequest, byte[] bytes, ChannelContext channelContext) throws Exception {
    Tio.remove(channelContext, "client closed");
    return null;
  }
}
