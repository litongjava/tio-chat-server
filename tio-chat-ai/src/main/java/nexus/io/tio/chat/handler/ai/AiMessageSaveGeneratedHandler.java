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
    // 参数在查询串中（conversationId、prompt、generatedContent），兼容放在请求体中
    Kv body = ParameterValidationUtils.bodyKv(request);
    Long conversationId = Values.toLong(param(request, body, "conversationId"));
    ParameterValidationUtils.required(conversationId, "conversationId不能为空");
    String prompt = param(request, body, "prompt");
    String generated = param(request, body, "generatedContent");
    if (generated == null) {
      generated = param(request, body, "content");
    }
    return ChatResp.ok(Aop.get(AiChatService.class).saveGenerated(LoginContext.uid(), conversationId, prompt, generated));
  }

  private static String param(HttpRequest request, Kv body, String name) {
    String value = request.getParam(name);
    return value != null ? value : body.getStr(name);
  }
}
