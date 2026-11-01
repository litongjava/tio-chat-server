package nexus.io.tio.chat.handler.ai;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.ai.AiChatService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/chat/conversation/my-list */
public class AiConversationMyListHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(AiChatService.class).myConversations(LoginContext.uid()));
  }
}
