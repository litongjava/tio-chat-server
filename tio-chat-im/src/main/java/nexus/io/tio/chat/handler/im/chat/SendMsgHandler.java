package nexus.io.tio.chat.handler.im.chat;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.model.im.chat.ChatMessageReq;
import nexus.io.tio.chat.service.im.chat.MessageService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/chat/msg */
public class SendMsgHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    ChatMessageReq req = ChatMessageReq.of(Values.toLong(body.get("roomId")), Values.toInt(body.get("msgType")), body.get("body"));
    req.setTemp(Values.isTrue(body.get("isTemp")) || Values.isTrue(body.get("temp")));
    req.setPushMessage(Values.isTrue(body.get("isPushMessage")) || Values.isTrue(body.get("pushMessage")));
    long uid = LoginContext.uid();
    MessageService messageService = Aop.get(MessageService.class);
    long msgId = messageService.send(req, uid);
    return ChatResp.ok(messageService.getMsgResp(msgId, uid));
  }
}
