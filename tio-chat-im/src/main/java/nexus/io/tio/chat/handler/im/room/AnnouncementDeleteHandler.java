package nexus.io.tio.chat.handler.im.room;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.group.AnnouncementService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/room/announcement/delete */
public class AnnouncementDeleteHandler {
  public HttpResponse handle(HttpRequest request) {
    Long id = ParameterValidationUtils.optionalLong(request, "id");
    if (id == null) {
      id = Values.toLong(ParameterValidationUtils.bodyKv(request).get("id"));
    }
    ParameterValidationUtils.required(id, "id不能为空");
    AnnouncementService service = Aop.get(AnnouncementService.class);
    return ChatResp.ok(service.delete(LoginContext.uid(), id));
  }
}
