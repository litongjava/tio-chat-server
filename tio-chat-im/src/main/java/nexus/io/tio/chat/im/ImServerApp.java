package nexus.io.tio.chat.im;

import nexus.io.tio.chat.boot.ChatApplication;

/**
 * 单独运行 luohuo-im-server，默认端口 18778，启动后注册到 Nacos
 */
public class ImServerApp {
  public static void main(String[] args) {
    ChatApplication.runService(ImServerApp.class, args, ImModule.SERVICE_NAME, ImModule.DEFAULT_PORT, new ImModule());
  }
}
