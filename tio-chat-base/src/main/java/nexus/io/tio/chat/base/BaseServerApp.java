package nexus.io.tio.chat.base;

import nexus.io.tio.chat.boot.ChatApplication;

/**
 * 单独运行 luohuo-base-server，默认端口 18764，启动后注册到 Nacos
 */
public class BaseServerApp {
  public static void main(String[] args) {
    ChatApplication.runService(BaseServerApp.class, args, BaseModule.SERVICE_NAME, BaseModule.DEFAULT_PORT, new BaseModule());
  }
}
