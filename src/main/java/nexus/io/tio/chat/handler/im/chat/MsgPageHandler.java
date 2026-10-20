package nexus.io.tio.chat.handler.im.chat;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.chat.ChatService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/chat/msg/page */
public class MsgPageHandler {
  public HttpResponse handle(HttpRequest request) {
    Long roomId = ParameterValidationUtils.requiredLong(request, "roomId");
    int pageSize = ParameterValidationUtils.optionalInt(request, "pageSize", 10);
    ChatService chatService = Aop.get(ChatService.class);
    return ChatResp.ok(chatService.page(roomId, request.getParam("cursor"), pageSize, false, LoginContext.uid()));
  }
}
