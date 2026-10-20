package nexus.io.tio.chat.handler.im.chat;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.chat.ChatService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/chat/msg/read/page */
public class MsgReadPageHandler {
  public HttpResponse handle(HttpRequest request) {
    Long msgId = ParameterValidationUtils.requiredLong(request, "msgId");
    Long searchType = ParameterValidationUtils.optionalLong(request, "searchType");
    int pageSize = ParameterValidationUtils.optionalInt(request, "pageSize", 10);
    ChatService chatService = Aop.get(ChatService.class);
    return ChatResp.ok(chatService.readPage(LoginContext.uid(), msgId, searchType, request.getParam("cursor"), pageSize));
  }
}
