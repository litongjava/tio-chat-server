package nexus.io.tio.chat;

import nexus.io.tio.boot.TioApplication;
import nexus.io.tio.boot.server.TioBootServer;
import nexus.io.tio.chat.config.ChatAppConfig;
import nexus.io.tio.chat.ws.ChatConnectionListener;

public class ChatServerApp {
  public static void main(String[] args) {
    long start = System.currentTimeMillis();
    TioBootServer.me().setServerAioListener(new ChatConnectionListener());
    TioApplication.run(ChatServerApp.class, new ChatAppConfig(), args);
    long end = System.currentTimeMillis();
    System.out.println((end - start) + "ms");
  }
}
