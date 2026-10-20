package nexus.io.tio.chat.handler.ai;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.ai.AiChatService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/ai/chat/message/save-generated-content */
public class AiMessageSaveGeneratedHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    Long conversationId = Values.toLong(body.get("conversationId"));
    if (conversationId == null) {
      conversationId = ParameterValidationUtils.optionalLong(request, "conversationId");
    }
    String content = body.getStr("content") == null ? request.getParam("content") : body.getStr("content");
    return ChatResp.ok(Aop.get(AiChatService.class).saveGenerated(LoginContext.uid(), conversationId, content));
  }
}
