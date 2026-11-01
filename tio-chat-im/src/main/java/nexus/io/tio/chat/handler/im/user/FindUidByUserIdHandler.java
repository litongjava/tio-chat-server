package nexus.io.tio.chat.handler.im.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.ImUserService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/user/findById */
public class FindUidByUserIdHandler {
  public HttpResponse handle(HttpRequest request) {
    Long userId = ParameterValidationUtils.requiredLong(request, "id");
    Long tenantId = ParameterValidationUtils.optionalLong(request, "tenantId");
    ImUserService service = Aop.get(ImUserService.class);
    return ChatResp.ok(service.findUidByUserId(userId, tenantId == null ? 1L : tenantId));
  }
}
