package nexus.io.tio.chat.handler.system;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.system.MapService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/system/anyTenant/map/geocoder/reverse */
public class MapReverseHandler {
  public HttpResponse handle(HttpRequest request) {
    double lat = Double.parseDouble(request.getParam("lat"));
    double lng = Double.parseDouble(request.getParam("lng"));
    return ChatResp.ok(Aop.get(MapService.class).reverse(lat, lng));
  }
}
