package nexus.io.tio.chat;

import java.util.TimeZone;

import nexus.io.tio.boot.TioApplication;
import nexus.io.tio.boot.server.TioBootServer;
import nexus.io.tio.chat.config.ChatAppConfig;
import nexus.io.tio.chat.ws.ChatConnectionListener;

public class ChatServerApp {
  public static void main(String[] args) {
    long start = System.currentTimeMillis();
    // 与数据库保持同一时区，保证 Java 与 SQL 生成的时间可以直接比较
    String timeZone = System.getProperty("app.time-zone", System.getenv().getOrDefault("APP_TIME_ZONE", "Asia/Shanghai"));
    TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
    TioBootServer.me().setServerAioListener(new ChatConnectionListener());
    TioApplication.run(ChatServerApp.class, new ChatAppConfig(), args);
    long end = System.currentTimeMillis();
    System.out.println((end - start) + "ms");
  }
}
