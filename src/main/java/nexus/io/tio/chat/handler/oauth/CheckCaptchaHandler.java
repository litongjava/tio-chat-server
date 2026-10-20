package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.oauth.CaptchaService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyTenant/checkCaptcha */
public class CheckCaptchaHandler {
  public HttpResponse handle(HttpRequest request) {
    String templateCode = request.getParam("templateCode", CaptchaService.GRANT_CAPTCHA);
    CaptchaService captchaService = Aop.get(CaptchaService.class);
    captchaService.check(request.getParam("key"), templateCode, request.getParam("code"));
    return ChatResp.ok(true);
  }
}
