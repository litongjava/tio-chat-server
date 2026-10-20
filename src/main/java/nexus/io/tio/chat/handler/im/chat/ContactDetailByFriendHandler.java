package nexus.io.tio.chat.handler.im.chat;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.chat.ContactQueryService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/chat/contact/detail/friend */
public class ContactDetailByFriendHandler {
  public HttpResponse handle(HttpRequest request) {
    Long id = ParameterValidationUtils.requiredLong(request, "id");
    int roomType = ParameterValidationUtils.optionalInt(request, "roomType", 2);
    ContactQueryService service = Aop.get(ContactQueryService.class);
    return ChatResp.ok(service.detailByFriend(LoginContext.uid(), id, roomType));
  }
}
