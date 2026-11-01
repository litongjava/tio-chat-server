package nexus.io.tio.chat.handler.oauth;

import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/oauth/check */
public class CheckTokenHandler {
  public HttpResponse handle(HttpRequest request) {
    LoginContext.required();
    return ChatResp.ok(true);
  }
}
