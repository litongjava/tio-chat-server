package nexus.io.tio.chat.handler;

import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /health */
public class HealthHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok("ok");
  }
}
