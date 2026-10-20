package nexus.io.tio.chat.handler.im.friend;

import java.util.ArrayList;
import java.util.List;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.FriendService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/user/friend/check */
public class FriendCheckHandler {
  public HttpResponse handle(HttpRequest request) {
    List<Long> uidList = new ArrayList<>();
    String[] values = request.getParamValues("uidList");
    if (values != null) {
      for (String value : values) {
        for (String id : value.split(",")) {
          Long uid = Values.toLong(id);
          if (uid != null) {
            uidList.add(uid);
          }
        }
      }
    }
    FriendService friendService = Aop.get(FriendService.class);
    return ChatResp.ok(friendService.check(LoginContext.uid(), uidList));
  }
}
