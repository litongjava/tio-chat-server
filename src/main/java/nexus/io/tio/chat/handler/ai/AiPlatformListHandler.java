package nexus.io.tio.chat.handler.ai;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiGenerateService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/platform/list */
public class AiPlatformListHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(AiGenerateService.class).platforms());
  }
}
