package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.model.oauth.QrIdReq;
import nexus.io.tio.chat.service.oauth.QrLoginService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.RequestUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/oauth/qrcode/scan */
public class QrScanHandler {
  public HttpResponse handle(HttpRequest request) {
    QrIdReq req = ParameterValidationUtils.parseBody(request, QrIdReq.class);
    ParameterValidationUtils.notBlank(req.getQrId(), "qrId不能为空");
    QrLoginService qrLoginService = Aop.get(QrLoginService.class);
    return ChatResp.ok(qrLoginService.scan(req.getQrId(), RequestUtils.ip(request)));
  }
}
