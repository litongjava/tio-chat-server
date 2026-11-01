package nexus.io.tio.chat.handler.im.chat;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.ContactService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/chat/setHide */
public class ContactSetHideHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    ContactService contactService = Aop.get(ContactService.class);
    return ChatResp.ok(contactService.setHide(LoginContext.uid(), Values.toLong(body.get("roomId")), Values.toBool(body.get("hide"))));
  }
}
