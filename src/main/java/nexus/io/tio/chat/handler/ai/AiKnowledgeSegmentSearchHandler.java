package nexus.io.tio.chat.handler.ai;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiKnowledgeService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/knowledge/segment/search */
public class AiKnowledgeSegmentSearchHandler {
  public HttpResponse handle(HttpRequest request) {
    Long knowledgeId = ParameterValidationUtils.requiredLong(request, "knowledgeId");
    Integer topK = ParameterValidationUtils.optionalLong(request, "topK") == null ? null : ParameterValidationUtils.optionalInt(request, "topK", 3);
    return ChatResp.ok(Aop.get(AiKnowledgeService.class).search(knowledgeId, request.getParam("content"), topK));
  }
}
