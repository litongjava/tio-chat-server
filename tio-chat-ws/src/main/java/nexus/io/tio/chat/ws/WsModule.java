package nexus.io.tio.chat.ws;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.server.TioBootServer;
import nexus.io.tio.chat.boot.ChatModule;
import nexus.io.tio.chat.config.WebSocketConfig;
import nexus.io.tio.chat.service.common.DelayedTaskService;
import nexus.io.tio.chat.ws.ChatConnectionListener;
import nexus.io.tio.chat.ws.call.CallService;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * WebSocket 连接、在线状态、推送与音视频信令（luohuo-ws-server）
 */
public class WsModule implements ChatModule {
  public static final String SERVICE_NAME = "luohuo-ws-server";
  public static final int DEFAULT_PORT = 19778;

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void beforeStart() {
    TioBootServer.me().setServerAioListener(new ChatConnectionListener());
  }

  @Override
  public void config(HttpRequestRouter router) throws Exception {
    new WebSocketConfig().config();
    Aop.get(DelayedTaskService.class).register(CallService.TASK_CALL, Aop.get(CallService.class)::onDelayed);
  }
}
