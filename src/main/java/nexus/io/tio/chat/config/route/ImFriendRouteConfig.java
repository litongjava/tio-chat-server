package nexus.io.tio.chat.config.route;

import nexus.io.tio.chat.handler.im.friend.FriendCheckHandler;
import nexus.io.tio.chat.handler.im.friend.FriendDeleteHandler;
import nexus.io.tio.chat.handler.im.friend.FriendPageHandler;
import nexus.io.tio.chat.handler.im.friend.FriendPermissionHandler;
import nexus.io.tio.chat.handler.im.friend.FriendRemarkHandler;
import nexus.io.tio.chat.handler.im.friend.FriendSearchHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class ImFriendRouteConfig {
  private static final String P = "/api/im/user/friend";

  public void config(HttpRequestRouter r) {
    r.get(P + "/check", new FriendCheckHandler()::handle);
    r.get(P + "/search", new FriendSearchHandler()::handle);
    r.delete(P + "", new FriendDeleteHandler()::handle);
    r.get(P + "/page", new FriendPageHandler()::handle);
    r.post(P + "/updateRemark", new FriendRemarkHandler()::handle);
    r.post(P + "/permissionSettings", new FriendPermissionHandler()::handle);
  }
}
