package nexus.io.tio.chat.config;

import nexus.io.context.BootConfiguration;
import nexus.io.tio.boot.server.TioBootServer;

public class ChatAppConfig implements BootConfiguration {

  @Override
  public void config() throws Exception {
    new JsonConfig().config();
    new DbConfig().config();
    new RedisConfig().config();
    TioBootServer.me().setExceptionHandler(new ChatExceptionHandler());
    new InterceptorConfig().config();
    new RouteConfig().config();
    new WebSocketConfig().config();
  }
}
