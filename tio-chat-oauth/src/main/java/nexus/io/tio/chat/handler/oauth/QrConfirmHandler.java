package nexus.io.tio.chat.handler.oauth;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.model.oauth.QrIdReq;
import nexus.io.tio.chat.service.oauth.QrLoginService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/oauth/qrcode/confirm */
public class QrConfirmHandler {
  public HttpResponse handle(HttpRequest request) {
    QrIdReq req = ParameterValidationUtils.parseBody(request, QrIdReq.class);
    ParameterValidationUtils.notBlank(req.getQrId(), "qrId不能为空");
    QrLoginService qrLoginService = Aop.get(QrLoginService.class);
    return ChatResp.ok(qrLoginService.confirm(req.getQrId(), LoginContext.required()));
  }
}
