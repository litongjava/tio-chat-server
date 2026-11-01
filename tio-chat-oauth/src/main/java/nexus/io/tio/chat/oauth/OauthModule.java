package nexus.io.tio.chat.oauth;

import nexus.io.tio.chat.boot.ChatModule;
import nexus.io.tio.chat.config.route.OauthExtRouteConfig;
import nexus.io.tio.chat.config.route.OauthRouteConfig;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * 登录、注册、token、验证码、扫码与第三方登录（luohuo-oauth-server）
 */
public class OauthModule implements ChatModule {
  public static final String SERVICE_NAME = "luohuo-oauth-server";
  public static final int DEFAULT_PORT = 18773;

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void config(HttpRequestRouter router) throws Exception {
    new OauthRouteConfig().config(router);
    new OauthExtRouteConfig().config(router);
  }
}
