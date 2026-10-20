package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.model.oauth.RegisterParam;
import nexus.io.tio.chat.service.oauth.RegisterService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/oauth/anyTenant/registerByMobile */
public class RegisterByMobileHandler {
  public HttpResponse handle(HttpRequest request) {
    RegisterParam param = ParameterValidationUtils.parseBody(request, RegisterParam.class);
    RegisterService registerService = Aop.get(RegisterService.class);
    return ChatResp.ok(registerService.registerByMobile(param));
  }
}
