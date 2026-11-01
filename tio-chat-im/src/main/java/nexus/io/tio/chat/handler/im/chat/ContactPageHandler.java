package nexus.io.tio.chat.handler.im.chat;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.chat.ContactQueryService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/chat/contact/page */
public class ContactPageHandler {
  public HttpResponse handle(HttpRequest request) {
    int pageSize = ParameterValidationUtils.optionalInt(request, "pageSize", 10);
    ContactQueryService service = Aop.get(ContactQueryService.class);
    return ChatResp.ok(service.page(LoginContext.uid(), request.getParam("cursor"), pageSize));
  }
}
