package nexus.io.tio.chat.handler.im.room;

import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.chat.MessageForwardService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/room/mergeMessage */
public class MergeMessageHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    MessageForwardService service = Aop.get(MessageForwardService.class);
    service.forward(LoginContext.uid(), Values.toLong(body.get("fromRoomId")), Values.toInt(body.get("type")), Values.toLongList(body.get("roomIds") instanceof List ? (List<?>) body.get("roomIds") : List.of()),
        Values.toLongList(body.get("messageIds") instanceof List ? (List<?>) body.get("messageIds") : List.of()));
    return ChatResp.ok();
  }
}
