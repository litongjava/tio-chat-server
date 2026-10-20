package nexus.io.tio.chat.handler.ai;

import java.util.List;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiKnowledgeService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/knowledge/segment/get-process-list */
public class AiKnowledgeSegmentProcessListHandler {
  public HttpResponse handle(HttpRequest request) {
    List<Long> ids = Values.toLongList(List.of(request.getParam("documentIds", "").split(",")));
    return ChatResp.ok(Aop.get(AiKnowledgeService.class).processList(ids));
  }
}
