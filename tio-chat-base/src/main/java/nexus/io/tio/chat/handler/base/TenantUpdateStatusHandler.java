package nexus.io.tio.chat.handler.base;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseExtService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/defTenant/updateStatus */
public class TenantUpdateStatusHandler {
  public HttpResponse handle(HttpRequest request) {
    Long id = ParameterValidationUtils.requiredLong(request, "id");
    int status = ParameterValidationUtils.optionalInt(request, "status", 10);
    return ChatResp.ok(Aop.get(BaseExtService.class).updateTenantStatus(id, status));
  }
}
