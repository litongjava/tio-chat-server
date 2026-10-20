package nexus.io.tio.chat.handler.im.chat;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.chat.ContactQueryService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/chat/contact/detail */
public class ContactDetailHandler {
  public HttpResponse handle(HttpRequest request) {
    Long roomId = ParameterValidationUtils.requiredLong(request, "id");
    ContactQueryService service = Aop.get(ContactQueryService.class);
    return ChatResp.ok(service.detail(LoginContext.uid(), roomId));
  }
}
