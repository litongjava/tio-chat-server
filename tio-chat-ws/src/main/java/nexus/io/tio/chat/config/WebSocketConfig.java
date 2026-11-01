package nexus.io.tio.chat.config;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.server.TioBootServer;
import nexus.io.tio.boot.websocket.WebSocketRouter;
import nexus.io.tio.chat.ws.AckProcessor;
import nexus.io.tio.chat.ws.ChatWebSocketHandler;
import nexus.io.tio.chat.ws.PushService;
import nexus.io.tio.chat.ws.ReadProcessor;
import nexus.io.tio.chat.ws.WsMessageDispatcher;
import nexus.io.tio.chat.ws.call.CallProcessors;

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
    CallProcessors.register();

    PushService pushService = Aop.get(PushService.class);
    pushService.subscribe();
  }
}
