package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.model.oauth.BindEmailReq;
import nexus.io.tio.chat.service.oauth.CaptchaService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/oauth/anyTenant/sendEmailCode */
public class SendEmailCodeHandler {
  public HttpResponse handle(HttpRequest request) {
    BindEmailReq req = ParameterValidationUtils.parseBody(request, BindEmailReq.class);
    ParameterValidationUtils.notBlank(req.getEmail(), "邮箱不能为空");
    String templateCode = req.getTemplateCode() == null ? CaptchaService.REGISTER_EMAIL : req.getTemplateCode();
    CaptchaService captchaService = Aop.get(CaptchaService.class);
    return ChatResp.ok(captchaService.sendEmailCode(req.getEmail(), templateCode));
  }
}
