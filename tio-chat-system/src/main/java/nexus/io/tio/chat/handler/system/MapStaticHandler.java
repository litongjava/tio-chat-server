package nexus.io.tio.chat.handler.system;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.system.MapService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/system/anyTenant/map/static */
public class MapStaticHandler {
  public HttpResponse handle(HttpRequest request) {
    double lat = Double.parseDouble(request.getParam("lat"));
    double lng = Double.parseDouble(request.getParam("lng"));
    int zoom = ParameterValidationUtils.optionalInt(request, "zoom", 18);
    int width = ParameterValidationUtils.optionalInt(request, "width", 600);
    int height = ParameterValidationUtils.optionalInt(request, "height", 400);
    return ChatResp.ok(Aop.get(MapService.class).staticMap(lat, lng, zoom, width, height));
  }
}
