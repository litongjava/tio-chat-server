package nexus.io.tio.chat.config;

import nexus.io.tio.boot.server.TioBootServer;
import nexus.io.tio.chat.handler.HealthHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class RouteConfig {

  public void config() {
    HttpRequestRouter r = TioBootServer.me().getRequestRouter();
    if (r == null) {
      return;
    }
    r.get("/health", new HealthHandler()::handle);
  }
}
