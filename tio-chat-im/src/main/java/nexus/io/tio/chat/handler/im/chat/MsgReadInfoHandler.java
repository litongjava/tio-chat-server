package nexus.io.tio.chat.handler.im.chat;

import java.util.ArrayList;
import java.util.List;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.chat.ChatService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/chat/msg/read */
public class MsgReadInfoHandler {
  public HttpResponse handle(HttpRequest request) {
    List<Long> msgIds = new ArrayList<>();
    String[] values = request.getParamValues("msgIds");
    if (values != null) {
      for (String value : values) {
        for (String id : value.split(",")) {
          Long msgId = Values.toLong(id);
          if (msgId != null) {
            msgIds.add(msgId);
          }
        }
      }
    }
    ChatService chatService = Aop.get(ChatService.class);
    return ChatResp.ok(chatService.readInfo(LoginContext.uid(), msgIds));
  }
}
