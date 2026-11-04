package nexus.io.tio.chat.gateway.handler;

import nexus.io.tio.boot.cloud.CloudContext;
import nexus.io.tio.boot.cloud.gateway.TioBootGateway;
import nexus.io.tio.boot.cloud.gateway.route.Route;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/doc.html：网关路由和各服务在线实例数的总览页 */
public class DocPageHandler {
  public HttpResponse handle(HttpRequest request) {
    StringBuilder html = new StringBuilder("<!doctype html><meta charset=\"utf-8\"><title>网关路由</title><h1>网关路由</h1>");
    html.append("<table border=\"1\" cellpadding=\"6\"><tr><th>路由</th><th>路径</th><th>目标</th><th>在线实例</th></tr>");
    for (Route route : TioBootGateway.getRouteLocator().getRoutes()) {
      int online = route.isLoadBalanced() ? CloudContext.getInstances(route.getServiceId()).size() : 0;
      html.append("<tr><td>").append(route.getId()).append("</td><td>").append(route.getDefinition().getPredicates())
          .append("</td><td>").append(route.getDefinition().getUri()).append("</td><td>").append(online).append("</td></tr>");
    }
    html.append("</table>");
    return TioRequestContext.getResponse().setString(html.toString(), "utf-8", "text/html;charset=utf-8");
  }
}
