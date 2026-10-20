package nexus.io.tio.chat.handler.base;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.base.BaseExtService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/base/defTenant/listTenantByUserId */
public class TenantListByUserHandler {
  public HttpResponse handle(HttpRequest request) {
    Long userId = ParameterValidationUtils.optionalLong(request, "userId");
    return ChatResp.ok(Aop.get(BaseExtService.class).tenantsOfUser(userId == null ? LoginContext.userId() : userId));
  }
}
