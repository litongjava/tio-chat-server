package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.model.oauth.PasswordUpdateReq;
import nexus.io.tio.chat.service.oauth.RegisterService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/oauth/anyTenant/password */
public class UpdatePasswordHandler {
  public HttpResponse handle(HttpRequest request) {
    PasswordUpdateReq req = ParameterValidationUtils.parseBody(request, PasswordUpdateReq.class);
    RegisterService registerService = Aop.get(RegisterService.class);
    return ChatResp.ok(registerService.updatePassword(req));
  }
}
