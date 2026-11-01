package nexus.io.tio.chat.system;

import nexus.io.tio.chat.boot.ChatApplication;

/**
 * 单独运行 luohuo-system-server，默认端口 19877，启动后注册到 Nacos
 */
public class SystemServerApp {
  public static void main(String[] args) {
    ChatApplication.runService(SystemServerApp.class, args, SystemModule.SERVICE_NAME, SystemModule.DEFAULT_PORT, new SystemModule());
  }
}
