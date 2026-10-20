package nexus.io.tio.chat.handler.ai;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiKnowledgeService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/knowledge/segment/split */
public class AiKnowledgeSegmentSplitHandler {
  public HttpResponse handle(HttpRequest request) {
    Integer maxTokens = ParameterValidationUtils.optionalInt(request, "segmentMaxTokens", 500);
    return ChatResp.ok(Aop.get(AiKnowledgeService.class).splitUrl(request.getParam("url"), maxTokens));
  }
}
