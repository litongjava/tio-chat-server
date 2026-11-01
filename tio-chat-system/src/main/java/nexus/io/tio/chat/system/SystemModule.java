package nexus.io.tio.chat.system;

import nexus.io.tio.chat.boot.ChatModule;
import nexus.io.tio.chat.config.route.SystemRouteConfig;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * 系统配置、对象存储、地图与翻译（luohuo-system-server）
 */
public class SystemModule implements ChatModule {
  public static final String SERVICE_NAME = "luohuo-system-server";
  public static final int DEFAULT_PORT = 19877;

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void config(HttpRequestRouter router) throws Exception {
    new SystemRouteConfig().config(router);
  }
}
