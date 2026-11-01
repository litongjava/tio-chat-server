package nexus.io.tio.chat.handler.ai;

import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.ai.AiChatService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** DELETE /api/ai/chat/conversation/delete-my */
public class AiConversationDeleteMyHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    List<Long> ids = body.get("ids") instanceof List ? Values.toLongList((List<?>) body.get("ids")) : List.of();
    if (body.get("id") != null) {
      ids = List.of(Values.toLong(body.get("id")));
    }
    if (ids.isEmpty() && ParameterValidationUtils.optionalLong(request, "id") != null) {
      ids = List.of(ParameterValidationUtils.optionalLong(request, "id"));
    }
    return ChatResp.ok(Aop.get(AiChatService.class).deleteConversations(LoginContext.uid(), ids));
  }
}
