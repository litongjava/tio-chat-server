package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.service.oauth.TokenService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyTenant/verify */
public class VerifyTokenHandler {
  public HttpResponse handle(HttpRequest request) {
    TokenService tokenService = Aop.get(TokenService.class);
    LoginSession session = tokenService.getSession(request.getParam("token"));
    return ChatResp.ok(session);
  }
}
