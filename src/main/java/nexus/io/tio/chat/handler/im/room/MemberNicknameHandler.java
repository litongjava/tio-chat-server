package nexus.io.tio.chat.handler.im.room;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.group.GroupService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/room/group/member/nickname */
public class MemberNicknameHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    GroupService groupService = Aop.get(GroupService.class);
    groupService.updateMemberNickname(Values.toLong(body.get("roomId")), Values.toLong(body.get("uid")), body.getStr("myName"),
        body.getStr("remark"));
    return ChatResp.ok();
  }
}
