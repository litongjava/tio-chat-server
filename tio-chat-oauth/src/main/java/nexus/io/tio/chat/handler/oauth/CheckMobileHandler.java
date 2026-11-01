package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.oauth.DefUserService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyTenant/checkMobile */
public class CheckMobileHandler {
  public HttpResponse handle(HttpRequest request) {
    String mobile = request.getParam("mobile");
    ParameterValidationUtils.notBlank(mobile, "手机号不能为空");
    DefUserService defUserService = Aop.get(DefUserService.class);
    return ChatResp.ok(defUserService.existsMobile(mobile));
  }
}
