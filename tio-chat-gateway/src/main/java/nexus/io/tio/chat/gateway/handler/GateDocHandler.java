package nexus.io.tio.chat.gateway.handler;

import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/gate/doc.html：跳转到接口总览页 */
public class GateDocHandler {
  public HttpResponse handle(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    response.sendRedirect("/api/doc.html");
    return response;
  }
}
