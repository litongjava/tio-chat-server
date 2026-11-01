package nexus.io.tio.chat.handler.base;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseAdminService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/base/defApplication/findApplicationResourceList */
public class ApplicationResourceListHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(BaseAdminService.class).applicationResources(false, false));
  }
}
