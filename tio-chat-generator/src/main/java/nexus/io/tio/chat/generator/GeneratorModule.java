package nexus.io.tio.chat.generator;

import nexus.io.tio.chat.boot.ChatModule;
import nexus.io.tio.chat.config.route.GeneratorRouteConfig;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * 在线代码生成（luohuo-generator-server）
 */
public class GeneratorModule implements ChatModule {
  public static final String SERVICE_NAME = "luohuo-generator-server";
  public static final int DEFAULT_PORT = 18780;

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void config(HttpRequestRouter router) throws Exception {
    new GeneratorRouteConfig().config(router);
  }
}
