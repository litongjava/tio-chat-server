package nexus.io.tio.chat.handler.oauth.ext;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.oauth.ThirdPartyAuthService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyTenant/{provider}/authorize-url */
public class AuthorizeUrlHandler {
  public HttpResponse handle(HttpRequest request) {
    String provider = request.getPathVar("provider").toLowerCase();
    return ChatResp.ok(Aop.get(ThirdPartyAuthService.class).authorizeUrl(provider, request.getParam("redirect")));
  }
}
