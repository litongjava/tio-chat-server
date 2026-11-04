package nexus.io.tio.chat.gateway.handler;

import nexus.io.tio.chat.gateway.service.OnlineServiceQuery;
import nexus.io.tio.chat.gateway.service.SessionService;
import nexus.io.tio.chat.gateway.utils.GatewayResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/gateway/findOnlineService，需要登录 */
public class OnlineServiceListHandler {
  private final SessionService sessionService;
  private final OnlineServiceQuery query = new OnlineServiceQuery();

  public OnlineServiceListHandler(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  public HttpResponse handle(HttpRequest request) {
    if (sessionService.find(request.getHeader("token")) == null) {
      return GatewayResp.fail(request, 401, "请先登录");
    }
    return GatewayResp.ok(request, query.options());
  }
}
