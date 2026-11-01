package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.oauth.CaptchaService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyTenant/captcha */
public class CaptchaHandler {
  public HttpResponse handle(HttpRequest request) {
    CaptchaService captchaService = Aop.get(CaptchaService.class);
    return ChatResp.ok(captchaService.createImage(request.getParam("key")));
  }
}
