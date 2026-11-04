package nexus.io.tio.chat.handler.im.system;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.ServerMonitorService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/defServer：服务监控 */
public class ServerInfoHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(ServerMonitorService.class).snapshot());
  }
}
