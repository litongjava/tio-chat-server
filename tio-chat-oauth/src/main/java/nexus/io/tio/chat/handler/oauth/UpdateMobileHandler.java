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

/** PUT /api/oauth/anyone/mobile */
public class UpdateMobileHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    String mobile = body.getStr("mobile");
    ParameterValidationUtils.notBlank(mobile, "手机号不能为空");
    String templateCode = body.getStr("templateCode") == null ? CaptchaService.MOBILE_EDIT : body.getStr("templateCode");
    Aop.get(CaptchaService.class).check(mobile, templateCode, body.getStr("code"));
    OauthUserService oauthUserService = Aop.get(OauthUserService.class);
    return ChatResp.ok(oauthUserService.updateMobile(LoginContext.userId(), mobile));
  }
}
