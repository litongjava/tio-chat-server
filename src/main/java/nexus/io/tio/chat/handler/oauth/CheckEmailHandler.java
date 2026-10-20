package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.oauth.RegisterService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyTenant/checkEmail */
public class CheckEmailHandler {
  public HttpResponse handle(HttpRequest request) {
    String email = request.getParam("email");
    ParameterValidationUtils.notBlank(email, "邮箱不能为空");
    RegisterService registerService = Aop.get(RegisterService.class);
    return ChatResp.ok(registerService.checkEmail(email));
  }
}
