package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.oauth.QrLoginService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.RequestUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyTenant/qr/status/query */
public class QrStatusQueryHandler {
  public HttpResponse handle(HttpRequest request) {
    String qrId = request.getParam("qrId");
    ParameterValidationUtils.notBlank(qrId, "qrId不能为空");
    QrLoginService qrLoginService = Aop.get(QrLoginService.class);
    return ChatResp.ok(qrLoginService.query(qrId, request.getParam("deviceHash"), request.getParam("deviceType"),
        request.getParam("clientId"), RequestUtils.ip(request)));
  }
}
