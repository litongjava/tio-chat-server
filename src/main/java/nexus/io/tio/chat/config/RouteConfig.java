package nexus.io.tio.chat.config;

import nexus.io.tio.boot.server.TioBootServer;
import nexus.io.tio.chat.config.route.ImChatRouteConfig;
import nexus.io.tio.chat.config.route.ImFeedRouteConfig;
import nexus.io.tio.chat.config.route.ImFriendRouteConfig;
import nexus.io.tio.chat.config.route.ImMiscRouteConfig;
import nexus.io.tio.chat.config.route.ImRoomRouteConfig;
import nexus.io.tio.chat.config.route.ImUserRouteConfig;
import nexus.io.tio.chat.config.route.OauthRouteConfig;
import nexus.io.tio.chat.config.route.SystemRouteConfig;
import nexus.io.tio.chat.handler.HealthHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class RouteConfig {

  public void config() {
    HttpRequestRouter r = TioBootServer.me().getRequestRouter();
    if (r == null) {
      return;
    }
    r.get("/health", new HealthHandler()::handle);
    new OauthRouteConfig().config(r);
    new ImUserRouteConfig().config(r);
    new ImChatRouteConfig().config(r);
    new ImFriendRouteConfig().config(r);
    new ImRoomRouteConfig().config(r);
    new ImMiscRouteConfig().config(r);
    new ImFeedRouteConfig().config(r);
    new SystemRouteConfig().config(r);
  }
}
