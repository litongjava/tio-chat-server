package nexus.io.tio.chat.handler.im.friend;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.FriendService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/user/friend/page */
public class FriendPageHandler {
  public HttpResponse handle(HttpRequest request) {
    Long uid = ParameterValidationUtils.optionalLong(request, "uid");
    int pageSize = ParameterValidationUtils.optionalInt(request, "pageSize", 10);
    FriendService friendService = Aop.get(FriendService.class);
    return ChatResp.ok(friendService.page(uid == null ? LoginContext.uid() : uid, request.getParam("cursor"), pageSize));
  }
}
