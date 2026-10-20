package nexus.io.tio.chat.handler.ai;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiKnowledgeService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/ai/knowledge/document/create */
public class AiKnowledgeDocumentCreateHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    return ChatResp.ok(Aop.get(AiKnowledgeService.class).createDocument(Values.toLong(body.get("knowledgeId")), body.getStr("name"), body.getStr("url"),
        Values.toInt(body.get("segmentMaxTokens"))));
  }
}
