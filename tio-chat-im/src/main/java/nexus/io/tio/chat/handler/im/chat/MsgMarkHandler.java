package nexus.io.tio.chat.handler.im.chat;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.chat.ChatService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/im/chat/msg/mark */
public class MsgMarkHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    Long msgId = Values.toLong(body.get("msgId"));
    ParameterValidationUtils.required(msgId, "msgId不能为空");
    ChatService chatService = Aop.get(ChatService.class);
    chatService.mark(LoginContext.uid(), msgId, Values.toInt(body.get("markType")), Values.toInt(body.get("actType")));
    return ChatResp.ok();
  }
}
