package nexus.io.tio.chat.handler.im.misc;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.oauth.CaptchaService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/anyTenant/sendCode */
public class SendCodeHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    ParameterValidationUtils.notBlank(body.getStr("email"), "邮箱不能为空");
    String templateCode = body.getStr("templateCode") == null ? CaptchaService.EMAIL_EDIT : body.getStr("templateCode");
    Aop.get(CaptchaService.class).sendEmailCode(body.getStr("email"), templateCode);
    return ChatResp.ok(true);
  }
}
