package nexus.io.tio.chat.handler.im.friend;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.FriendService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/user/friend/search */
public class FriendSearchHandler {
  public HttpResponse handle(HttpRequest request) {
    FriendService friendService = Aop.get(FriendService.class);
    return ChatResp.ok(friendService.search(request.getParam("key")));
  }
}
