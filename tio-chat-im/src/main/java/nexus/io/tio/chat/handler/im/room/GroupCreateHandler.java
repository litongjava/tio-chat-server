package nexus.io.tio.chat.handler.im.room;

import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.group.GroupService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/room/group */
public class GroupCreateHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    GroupService groupService = Aop.get(GroupService.class);
    long roomId = groupService.create(LoginContext.uid(), body.getStr("groupName"), Values.toLongList(body.get("uidList") instanceof List ? (List<?>) body.get("uidList") : List.of()));
    return ChatResp.ok(Kv.by("id", roomId));
  }
}
