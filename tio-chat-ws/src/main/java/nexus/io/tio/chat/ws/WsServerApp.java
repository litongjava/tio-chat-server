package nexus.io.tio.chat.ws;

import nexus.io.tio.chat.boot.ChatApplication;

/**
 * 单独运行 luohuo-ws-server，默认端口 19778，启动后注册到 Nacos
 */
public class WsServerApp {
  public static void main(String[] args) {
    ChatApplication.runService(WsServerApp.class, args, WsModule.SERVICE_NAME, WsModule.DEFAULT_PORT, new WsModule());
  }
}
