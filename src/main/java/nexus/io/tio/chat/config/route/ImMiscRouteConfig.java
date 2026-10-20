package nexus.io.tio.chat.config.route;

import nexus.io.tio.chat.handler.im.misc.AdminActiveUsersHandler;
import nexus.io.tio.chat.handler.im.misc.AdminHomeStatsHandler;
import nexus.io.tio.chat.handler.im.misc.AdminLoginRankHandler;
import nexus.io.tio.chat.handler.im.misc.EmojiAddHandler;
import nexus.io.tio.chat.handler.im.misc.EmojiDeleteHandler;
import nexus.io.tio.chat.handler.im.misc.EmojiListHandler;
import nexus.io.tio.chat.handler.im.misc.PrivacyGetHandler;
import nexus.io.tio.chat.handler.im.misc.PrivacyUpdateHandler;
import nexus.io.tio.chat.handler.im.misc.SendCodeHandler;
import nexus.io.tio.chat.handler.im.misc.SensitiveWordCheckHandler;
import nexus.io.tio.chat.handler.im.misc.SensitiveWordRefreshHandler;
import nexus.io.tio.chat.handler.im.misc.UserStateChangeHandler;
import nexus.io.tio.chat.handler.im.misc.UserStateListHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class ImMiscRouteConfig {
  private static final String P = "/api/im";

  public void config(HttpRequestRouter r) {
    r.get(P + "/user/emoji/list", new EmojiListHandler()::handle);
    r.post(P + "/user/emoji", new EmojiAddHandler()::handle);
    r.delete(P + "/user/emoji", new EmojiDeleteHandler()::handle);
    r.post(P + "/user/state/changeState", new UserStateChangeHandler()::handle);
    r.get(P + "/user/state/list", new UserStateListHandler()::handle);
    r.get(P + "/privacy", new PrivacyGetHandler()::handle);
    r.post(P + "/privacy", new PrivacyUpdateHandler()::handle);
    r.post(P + "/sensitiveWord/refresh", new SensitiveWordRefreshHandler()::handle);
    r.post(P + "/sensitiveWord/check", new SensitiveWordCheckHandler()::handle);
    r.post(P + "/anyTenant/sendCode", new SendCodeHandler()::handle);
    r.get(P + "/admin/stats/home", new AdminHomeStatsHandler()::handle);
    r.get(P + "/admin/stats/login-rank", new AdminLoginRankHandler()::handle);
    r.get(P + "/admin/stats/active-users", new AdminActiveUsersHandler()::handle);
  }
}
