package nexus.io.tio.chat.handler.im.room;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.group.GroupService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/room/group/all */
public class GroupAllHandler {
  public HttpResponse handle(HttpRequest request) {
    GroupService groupService = Aop.get(GroupService.class);
    return ChatResp.ok(groupService.allGroups());
  }
}
