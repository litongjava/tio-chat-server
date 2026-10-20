package nexus.io.tio.chat.handler.im.room;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.group.AnnouncementService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/room/announcement/list */
public class AnnouncementListHandler {
  public HttpResponse handle(HttpRequest request) {
    Long roomId = ParameterValidationUtils.requiredLong(request, "roomId");
    int current = ParameterValidationUtils.optionalInt(request, "current", 1);
    int size = ParameterValidationUtils.optionalInt(request, "size", 10);
    AnnouncementService service = Aop.get(AnnouncementService.class);
    return ChatResp.ok(service.list(roomId, current, size));
  }
}
