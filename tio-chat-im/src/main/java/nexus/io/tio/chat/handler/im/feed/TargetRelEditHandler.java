package nexus.io.tio.chat.handler.im.feed;

import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.feed.TargetService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/target/rel/edit */
public class TargetRelEditHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    List<Long> targetIds = body.get("targetIds") instanceof List ? Values.toLongList((List<?>) body.get("targetIds")) : List.of();
    TargetService targetService = Aop.get(TargetService.class);
    return ChatResp.ok(targetService.editRel(LoginContext.uid(), Values.toLong(body.get("friendId")), targetIds));
  }
}
