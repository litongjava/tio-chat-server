package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.oauth.CaptchaService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyTenant/sendSmsCode */
public class SendSmsCodeHandler {
  public HttpResponse handle(HttpRequest request) {
    String mobile = request.getParam("mobile");
    String templateCode = request.getParam("templateCode");
    ParameterValidationUtils.notBlank(mobile, "手机号不能为空");
    ParameterValidationUtils.notBlank(templateCode, "templateCode不能为空");
    CaptchaService captchaService = Aop.get(CaptchaService.class);
    return ChatResp.ok(captchaService.sendSmsCode(mobile, templateCode));
  }
}
