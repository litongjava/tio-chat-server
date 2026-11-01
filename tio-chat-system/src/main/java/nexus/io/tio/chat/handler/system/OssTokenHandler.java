package nexus.io.tio.chat.handler.system;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.system.StorageService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/system/anyTenant/ossToken */
public class OssTokenHandler {
  public HttpResponse handle(HttpRequest request) {
    StorageService service = Aop.get(StorageService.class);
    return ChatResp.ok(service.token(request.getParam("scene", "chat"), request.getParam("fileName")));
  }
}
