package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.model.oauth.RefreshReq;
import nexus.io.tio.chat.service.oauth.LoginService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.RequestUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/oauth/anyTenant/refresh */
public class RefreshTokenHandler {
  public HttpResponse handle(HttpRequest request) {
    RefreshReq req = ParameterValidationUtils.parseBody(request, RefreshReq.class);
    LoginService loginService = Aop.get(LoginService.class);
    return ChatResp.ok(loginService.refresh(req.getRefreshToken(), RequestUtils.ip(request)));
  }
}
