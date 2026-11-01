package nexus.io.tio.chat.handler.im.chat;

import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.chat.ChatService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/chat/msg/list */
public class MsgListHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    List<Long> msgIds = body.get("msgIds") instanceof List ? Values.toLongList((List<?>) body.get("msgIds")) : null;
    boolean async = Values.isTrue(body.get("async"));
    ChatService chatService = Aop.get(ChatService.class);
    return ChatResp.ok(chatService.list(msgIds, async, LoginContext.uid()));
  }
}
