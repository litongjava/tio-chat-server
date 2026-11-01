package nexus.io.tio.chat.handler.im.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.BlackService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/user/black/page */
public class BlackPageHandler {
  public HttpResponse handle(HttpRequest request) {
    int pageNo = ParameterValidationUtils.optionalInt(request, "pageNo", 1);
    int pageSize = ParameterValidationUtils.optionalInt(request, "pageSize", 10);
    BlackService service = Aop.get(BlackService.class);
    return ChatResp.ok(service.page(request.getParam("target"), pageNo, pageSize));
  }
}
