package nexus.io.tio.chat.handler.im.misc;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.ImExtraService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/admin/stats/home */
public class AdminHomeStatsHandler {
  public HttpResponse handle(HttpRequest request) {
    ImExtraService service = Aop.get(ImExtraService.class);
    return ChatResp.ok(service.homeStats());
  }
}
