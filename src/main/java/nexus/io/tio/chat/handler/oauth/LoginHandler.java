package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.model.oauth.LoginParam;
import nexus.io.tio.chat.model.oauth.LoginResult;
import nexus.io.tio.chat.service.oauth.LoginService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.RequestUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/oauth/anyTenant/login */
public class LoginHandler {
  public HttpResponse handle(HttpRequest request) {
    LoginParam param = ParameterValidationUtils.parseBody(request, LoginParam.class);
    LoginService loginService = Aop.get(LoginService.class);
    LoginResult result = loginService.login(param, request.getHeader("authorization"), RequestUtils.ip(request));
    return ChatResp.ok(result);
  }
}
