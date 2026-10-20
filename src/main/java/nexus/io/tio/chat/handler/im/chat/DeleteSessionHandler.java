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

/** DELETE /api/im/chat/delete */
public class DeleteSessionHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    Long roomId = Values.toLong(body.get("roomId"));
    if (roomId == null) {
      roomId = ParameterValidationUtils.optionalLong(request, "roomId");
    }
    ParameterValidationUtils.required(roomId, "roomId不能为空");
    ContactService contactService = Aop.get(ContactService.class);
    return ChatResp.ok(contactService.setHide(LoginContext.uid(), roomId, true));
  }
}
