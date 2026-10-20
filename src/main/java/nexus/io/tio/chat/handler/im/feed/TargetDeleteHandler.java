package nexus.io.tio.chat.handler.im.feed;

import java.util.List;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.feed.TargetService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.json.JsonUtils;

/** POST /api/im/target/del */
public class TargetDeleteHandler {
  public HttpResponse handle(HttpRequest request) {
    String body = request.getBodyString();
    List<Long> ids = body == null || body.isBlank() ? List.of() : Values.toLongList(JsonUtils.parseArray(body, Object.class));
    TargetService targetService = Aop.get(TargetService.class);
    return ChatResp.ok(targetService.remove(ids));
  }
}
