package nexus.io.tio.chat.handler.im.misc;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.SensitiveWordService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/sensitiveWord/refresh */
public class SensitiveWordRefreshHandler {
  public HttpResponse handle(HttpRequest request) {
    Aop.get(SensitiveWordService.class).refresh();
    return ChatResp.ok(true);
  }
}
