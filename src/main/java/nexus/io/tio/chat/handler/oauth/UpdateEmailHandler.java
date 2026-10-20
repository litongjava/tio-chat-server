package nexus.io.tio.chat.handler.oauth;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.oauth.CaptchaService;
import nexus.io.tio.chat.service.oauth.OauthUserService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/oauth/anyone/email */
public class UpdateEmailHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    String email = body.getStr("email");
    ParameterValidationUtils.notBlank(email, "邮箱不能为空");
    String templateCode = body.getStr("templateCode") == null ? CaptchaService.EMAIL_EDIT : body.getStr("templateCode");
    Aop.get(CaptchaService.class).check(email, templateCode, body.getStr("code"));
    OauthUserService oauthUserService = Aop.get(OauthUserService.class);
    return ChatResp.ok(oauthUserService.updateEmail(LoginContext.userId(), email));
  }
}
