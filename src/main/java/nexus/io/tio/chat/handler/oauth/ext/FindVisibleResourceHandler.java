package nexus.io.tio.chat.handler.oauth.ext;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseAdminService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyone/findVisibleResource */
public class FindVisibleResourceHandler {
  public HttpResponse handle(HttpRequest request) {
    Long employeeId = ParameterValidationUtils.requiredLong(request, "employeeId");
    return ChatResp.ok(Aop.get(BaseAdminService.class).employeeResourceCodes(employeeId, ParameterValidationUtils.optionalLong(request, "applicationId")));
  }
}
