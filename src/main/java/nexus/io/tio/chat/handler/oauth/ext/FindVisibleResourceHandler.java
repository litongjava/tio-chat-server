package nexus.io.tio.chat.handler.oauth.ext;

import nexus.io.tio.chat.facade.Facades;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyone/findVisibleResource */
public class FindVisibleResourceHandler {
  public HttpResponse handle(HttpRequest request) {
    Long employeeId = ParameterValidationUtils.requiredLong(request, "employeeId");
    return ChatResp.ok(Facades.base().employeeResourceCodes(employeeId, ParameterValidationUtils.optionalLong(request, "applicationId")));
  }
}
