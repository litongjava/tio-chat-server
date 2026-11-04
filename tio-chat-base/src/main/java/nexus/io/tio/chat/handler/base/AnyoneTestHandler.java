package nexus.io.tio.chat.handler.base;

import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/base/anyone/base/test?id=100：等待 id 毫秒后返回 id，最多等待 3 秒，用于检查调用链路和超时 */
public class AnyoneTestHandler {
  private static final long MAX_SLEEP_MILLIS = 3000;

  public HttpResponse handle(HttpRequest request) throws InterruptedException {
    Long id = ParameterValidationUtils.optionalLong(request, "id");
    if (id != null && id > 0) {
      Thread.sleep(Math.min(id, MAX_SLEEP_MILLIS));
    }
    return ChatResp.ok(id);
  }
}
