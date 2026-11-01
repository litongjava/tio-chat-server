package nexus.io.tio.chat.handler.im.room;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.group.GroupService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** DELETE /api/im/room/group/member/exit */
public class GroupExitHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    GroupService groupService = Aop.get(GroupService.class);
    groupService.exit(false, LoginContext.uid(), Values.toLong(body.get("roomId")));
    return ChatResp.ok();
  }
}
