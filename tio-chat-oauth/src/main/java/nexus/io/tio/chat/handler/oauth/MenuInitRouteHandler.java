package nexus.io.tio.chat.handler.oauth;

import nexus.io.tio.chat.facade.Facades;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyTenant/menu/initRoute：不需要登录的公共路由 */
public class MenuInitRouteHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Facades.base().constantRoutes(ParameterValidationUtils.optionalLong(request, "applicationId")));
  }
}
