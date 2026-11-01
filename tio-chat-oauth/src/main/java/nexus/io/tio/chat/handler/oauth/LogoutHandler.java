package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.oauth.LoginService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/oauth/anyUser/logout */
public class LogoutHandler {
  public HttpResponse handle(HttpRequest request) {
    LoginService loginService = Aop.get(LoginService.class);
    return ChatResp.ok(loginService.logout(LoginContext.get()));
  }
}
