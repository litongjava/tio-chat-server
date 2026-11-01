package nexus.io.tio.chat.config;

import nexus.io.tio.chat.facade.Facades;
import nexus.io.tio.chat.service.base.BaseFacadeLocal;
import nexus.io.tio.chat.service.im.ImFacadeLocal;
import nexus.io.context.BootConfiguration;
import nexus.io.tio.boot.server.TioBootServer;

public class ChatAppConfig implements BootConfiguration {

  @Override
  public void config() throws Exception {
    Facades.setBase(new BaseFacadeLocal());
    Facades.setIm(new ImFacadeLocal());
    new CloudConfig().config();
    new JsonConfig().config();
    new DbConfig().config();
    new RedisConfig().config();
    TioBootServer.me().setExceptionHandler(new ChatExceptionHandler());
    new InterceptorConfig().config();
    new RouteConfig().config();
    new WebSocketConfig().config();
  }
}
