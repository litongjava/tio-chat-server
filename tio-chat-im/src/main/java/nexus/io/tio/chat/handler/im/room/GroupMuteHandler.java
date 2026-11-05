package nexus.io.tio.chat.handler.im.room;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.group.GroupMuteService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/room/group/mute */
public class GroupMuteHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    Long roomId = Values.toLong(body.get("roomId"));
    Long uid = Values.toLong(body.get("uid"));
    ParameterValidationUtils.required(roomId, "房间号不能为空");
    ParameterValidationUtils.required(uid, "uid不能为空");
    return ChatResp.ok(Aop.get(GroupMuteService.class).mute(LoginContext.uid(), roomId, uid, Values.toLong(body.get("duration"))));
  }
}
