package nexus.io.tio.chat.generator;

import nexus.io.tio.chat.boot.ChatApplication;

/**
 * 单独运行 luohuo-generator-server，默认端口 18780，启动后注册到 Nacos
 */
public class GeneratorServerApp {
  public static void main(String[] args) {
    ChatApplication.runService(GeneratorServerApp.class, args, GeneratorModule.SERVICE_NAME, GeneratorModule.DEFAULT_PORT,
        new GeneratorModule());
  }
}
