package nexus.io.tio.chat.config.route;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.handler.im.system.DatasourceTestConnectHandler;
import nexus.io.tio.chat.handler.im.system.ServerInfoHandler;
import nexus.io.tio.chat.handler.im.system.UserBindOauthHandler;
import nexus.io.tio.chat.handler.im.system.UserRegisterHandler;
import nexus.io.tio.chat.handler.im.wx.WxPortalCallbackHandler;
import nexus.io.tio.chat.handler.im.wx.WxPortalMessageHandler;
import nexus.io.tio.chat.handler.im.wx.WxPortalVerifyHandler;
import nexus.io.tio.chat.service.base.crud.CrudDef;
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
    r.get(P + "/wx/portal/public", new WxPortalVerifyHandler()::handle);
    r.post(P + "/wx/portal/public", new WxPortalMessageHandler()::handle);
    r.get(P + "/wx/portal/public/callBack", new WxPortalCallbackHandler()::handle);
    r.post(P + "/user/register", new UserRegisterHandler()::handle);
    r.post(P + "/user/bindOAuth", new UserBindOauthHandler()::handle);
    r.get(P + "/defServer", new ServerInfoHandler()::handle);
    r.post(P + "/defDatasourceConfig/testConnect", new DatasourceTestConnectHandler()::handle);
    Aop.get(CrudRouteRegistrar.class).register(r, P, CrudDef.of("/defDatasourceConfig", "def_datasource_config")
        .like("name", "username", "url", "driver_class_name"));
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
