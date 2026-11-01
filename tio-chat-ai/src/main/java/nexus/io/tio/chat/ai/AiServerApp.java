package nexus.io.tio.chat.ai;

import nexus.io.tio.chat.boot.ChatApplication;

/**
 * 单独运行 luohuo-ai-server，默认端口 18877，启动后注册到 Nacos
 */
public class AiServerApp {
  public static void main(String[] args) {
    ChatApplication.runService(AiServerApp.class, args, AiModule.SERVICE_NAME, AiModule.DEFAULT_PORT, new AiModule());
  }
}
