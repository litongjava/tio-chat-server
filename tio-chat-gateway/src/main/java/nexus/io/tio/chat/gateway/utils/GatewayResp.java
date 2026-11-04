package nexus.io.tio.chat.gateway.utils;

import com.jfinal.kit.Kv;

import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/**
 * 网关自身接口的响应，结构与各服务一致
 */
public class GatewayResp {
  private static final int SUCCESS = 200;

  public static HttpResponse ok(HttpRequest request, Object data) {
    return respond(request, SUCCESS, data, "ok");
  }

  public static HttpResponse fail(HttpRequest request, int code, String msg) {
    return respond(request, code, null, msg);
  }

  private static HttpResponse respond(HttpRequest request, int code, Object data, String msg) {
    return TioRequestContext.getResponse().setJson(Kv.by("code", code).set("data", data).set("msg", msg)
        .set("path", request.getRequestURI()).set("version", "1.0.0").set("timestamp", System.currentTimeMillis())
        .set("success", code == SUCCESS));
  }
}
