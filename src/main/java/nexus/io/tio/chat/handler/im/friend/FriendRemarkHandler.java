package nexus.io.tio.chat.handler.im.friend;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.FriendService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/user/friend/updateRemark */
public class FriendRemarkHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    FriendService friendService = Aop.get(FriendService.class);
    return ChatResp.ok(friendService.updateRemark(LoginContext.uid(), Values.toLong(body.get("targetUid")), body.getStr("remark")));
  }
}
