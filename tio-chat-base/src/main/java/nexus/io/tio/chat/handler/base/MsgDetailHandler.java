package nexus.io.tio.chat.handler.base;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseExtService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/base/extendMsg/{id:[0-9]+} */
public class MsgDetailHandler {
  public HttpResponse handle(HttpRequest request) {
    Long id = Values.toLong(request.getPathVar("id"));
    return ChatResp.ok(Aop.get(BaseExtService.class).msgDetail(id));
  }
}
