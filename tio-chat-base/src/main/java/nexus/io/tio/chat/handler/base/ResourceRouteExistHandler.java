package nexus.io.tio.chat.handler.base;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.AdminResourceService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/base/defResource/isRouteExist */
public class ResourceRouteExistHandler {
  public HttpResponse handle(HttpRequest request) {
    AdminResourceService service = Aop.get(AdminResourceService.class);
    return ChatResp.ok(service.routeExists(request.getParam("routeName")));
  }
}
