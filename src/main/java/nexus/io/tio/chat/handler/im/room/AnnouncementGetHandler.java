package nexus.io.tio.chat.handler.im.room;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.group.AnnouncementService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/room/announcement */
public class AnnouncementGetHandler {
  public HttpResponse handle(HttpRequest request) {
    Long roomId = ParameterValidationUtils.requiredLong(request, "roomId");
    Long id = ParameterValidationUtils.requiredLong(request, "announcementId");
    AnnouncementService service = Aop.get(AnnouncementService.class);
    return ChatResp.ok(service.get(LoginContext.uid(), roomId, id));
  }
}
