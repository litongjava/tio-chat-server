package nexus.io.tio.chat.gateway.handler;

import lombok.extern.slf4j.Slf4j;
import nexus.io.tio.boot.cloud.gateway.GatewayExchange;
import nexus.io.tio.chat.gateway.utils.GatewayResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** /api/fallback：服务不可用时返回的统一提示 */
@Slf4j
public class FallbackHandler {
  private static final int SYSTEM_TIMEOUT = -2;

  public HttpResponse handle(HttpRequest request) {
    return GatewayResp.fail(request, SYSTEM_TIMEOUT, "系统维护中~请稍后再试~");
  }

  /** 上游无法响应时的降级：服务不可用或者熔断，响应和 /api/fallback 相同 */
  public HttpResponse handle(GatewayExchange exchange, int status, String message) {
    log.warn("upstream failed: {} {} {}", exchange.getRequest().getRequestURI(), status, message);
    return handle(exchange.getRequest());
  }
}
