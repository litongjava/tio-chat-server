package nexus.io.tio.chat.handler.im.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.BlackService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/user/black/isBlack */
public class IsBlackHandler {
  public HttpResponse handle(HttpRequest request) {
    Long uid = ParameterValidationUtils.optionalLong(request, "uid");
    BlackService service = Aop.get(BlackService.class);
    return ChatResp.ok(service.isBlack(uid, request.getParam("ip")));
  }
}
