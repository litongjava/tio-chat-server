package nexus.io.tio.chat.handler.ai;

import java.util.Set;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiCrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.RequestParams;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/chat-role/page */
public class AiChatRolePageHandler {
  public HttpResponse handle(HttpRequest request) {
    AiCrudService crud = Aop.get(AiCrudService.class);
    // 公开角色（public_status=1）与自己的角色
    return ChatResp.ok(crud.page("ai_chat_role", RequestParams.toKv(request), Set.of("name", "category"), "(public_status=1 or user_id=?)", crud.uid()));
  }
}
