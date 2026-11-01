package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.oauth.OauthUserService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyone/getUserInfo */
public class GetUserInfoHandler {
  public HttpResponse handle(HttpRequest request) {
    OauthUserService oauthUserService = Aop.get(OauthUserService.class);
    return ChatResp.ok(oauthUserService.getUserInfo(LoginContext.required()));
  }
}
