package nexus.io.tio.chat.handler.im.feed;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.feed.TargetService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/target/detail */
public class TargetDetailHandler {
  public HttpResponse handle(HttpRequest request) {
    Long id = ParameterValidationUtils.requiredLong(request, "id");
    TargetService targetService = Aop.get(TargetService.class);
    return ChatResp.ok(targetService.detail(id));
  }
}
