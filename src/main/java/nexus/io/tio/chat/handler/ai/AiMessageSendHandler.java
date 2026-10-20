package nexus.io.tio.chat.handler.ai;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.ai.AiChatService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/ai/chat/message/send */
public class AiMessageSendHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    ParameterValidationUtils.notBlank(body.getStr("content"), "内容不能为空");
    return ChatResp.ok(Aop.get(AiChatService.class).send(LoginContext.uid(), Values.toLong(body.get("conversationId")), body.getStr("content"),
        body.get("useContext") == null || Values.isTrue(body.get("useContext"))));
  }
}
