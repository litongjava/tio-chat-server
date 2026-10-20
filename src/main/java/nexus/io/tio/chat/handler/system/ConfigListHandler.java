package nexus.io.tio.chat.handler.system;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.system.SystemService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/system/anyTenant/config/list */
public class ConfigListHandler {
  public HttpResponse handle(HttpRequest request) {
    SystemService service = Aop.get(SystemService.class);
    return ChatResp.ok(service.configList(request.getParam("type"), request.getParam("configName"), request.getParam("configKey")));
  }
}
