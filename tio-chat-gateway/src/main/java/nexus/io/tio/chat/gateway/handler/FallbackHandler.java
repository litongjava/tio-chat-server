package nexus.io.tio.chat.gateway.handler;

import nexus.io.tio.chat.gateway.utils.GatewayResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** /api/fallback：服务不可用时返回的统一提示 */
public class FallbackHandler {
  private static final int SYSTEM_TIMEOUT = -2;

  public HttpResponse handle(HttpRequest request) {
    return GatewayResp.fail(request, SYSTEM_TIMEOUT, "系统维护中~请稍后再试~");
  }
}
