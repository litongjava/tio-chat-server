package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.oauth.QrLoginService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.RequestUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyTenant/qr/generate */
public class QrGenerateHandler {
  public HttpResponse handle(HttpRequest request) {
    QrLoginService qrLoginService = Aop.get(QrLoginService.class);
    return ChatResp.ok(qrLoginService.generate(RequestUtils.ip(request)));
  }
}
