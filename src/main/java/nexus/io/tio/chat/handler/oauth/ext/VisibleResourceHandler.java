package nexus.io.tio.chat.handler.oauth.ext;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.base.BaseExtService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyone/visible/resource */
public class VisibleResourceHandler {
  public HttpResponse handle(HttpRequest request) {
    Long employeeId = ParameterValidationUtils.optionalLong(request, "employeeId");
    Long applicationId = ParameterValidationUtils.optionalLong(request, "applicationId");
    return ChatResp.ok(Aop.get(BaseExtService.class).visibleResource(employeeId == null ? LoginContext.uid() : employeeId, applicationId));
  }
}
