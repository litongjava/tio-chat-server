package nexus.io.tio.chat.config;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.server.TioBootServer;
import nexus.io.tio.boot.websocket.WebSocketRouter;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.ws.OffLineResp;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.service.oauth.LoginEventService;
import nexus.io.tio.chat.ws.AckProcessor;
import nexus.io.tio.chat.ws.ChatWebSocketHandler;
import nexus.io.tio.chat.ws.PushService;
import nexus.io.tio.chat.ws.ReadProcessor;
import nexus.io.tio.chat.ws.WsMessageDispatcher;
import nexus.io.tio.core.Tio;
import nexus.io.tio.server.ServerTioConfig;

public class WebSocketConfig {

  public void config() {
    WebSocketRouter router = TioBootServer.me().getWebSocketRouter();
    if (router == null) {
      return;
    }
    router.add("/api/ws/ws", new ChatWebSocketHandler());
    router.add("/ws", new ChatWebSocketHandler());

    WsMessageDispatcher.register(15, new AckProcessor());
    WsMessageDispatcher.register(16, new ReadProcessor());

    PushService pushService = Aop.get(PushService.class);
    pushService.subscribe();

    // 同类设备重复登录时通知旧设备下线，并断开旧 token 的连接
    LoginEventService.addKickoutListener((session, kickedToken) -> {
      OffLineResp resp = new OffLineResp(session.getUid(), session.getDeviceType(), session.getClientId(), null,
          kickedToken);
      pushService.push(WsBaseResp.of(WsRespType.TOKEN_EXPIRED, resp), session.getUid());
      ServerTioConfig config = TioBootServer.me().getServerTioConfig();
      if (config != null) {
        Tio.unbindToken(config, kickedToken);
      }
    });
  }
}
