package nexus.io.tio.chat.handler.base;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseAdminService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/base/basePosition/check */
public class PositionCheckHandler {
  public HttpResponse handle(HttpRequest request) {
    Long id = ParameterValidationUtils.optionalLong(request, "id");
    BaseAdminService service = Aop.get(BaseAdminService.class);
    return ChatResp.ok(service.exists("base_position", "name", request.getParam("name"), id, "org_id", ParameterValidationUtils.optionalLong(request, "orgId")));
  }
}
