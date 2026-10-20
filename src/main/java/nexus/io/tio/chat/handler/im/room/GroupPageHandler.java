package nexus.io.tio.chat.handler.im.room;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.group.GroupService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/room/group/page */
public class GroupPageHandler {
  public HttpResponse handle(HttpRequest request) {
    int pageNo = ParameterValidationUtils.optionalInt(request, "pageNo", 1);
    int pageSize = ParameterValidationUtils.optionalInt(request, "pageSize", 10);
    GroupService groupService = Aop.get(GroupService.class);
    return ChatResp.ok(groupService.groupPage(pageNo, pageSize, request.getParam("groupNameKeyword"), request.getParam("memberNameKeyword")));
  }
}
