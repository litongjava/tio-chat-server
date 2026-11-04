package ${basePackage}.handler;

import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /health */
public class HealthHandler {

  public HttpResponse handle(HttpRequest request) {
    return TioRequestContext.getResponse().setJson(RespBodyVo.ok("${applicationName}"));
  }
}
