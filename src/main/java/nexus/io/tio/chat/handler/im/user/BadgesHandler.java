package nexus.io.tio.chat.handler.im.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.ImUserInfoService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/user/badges */
public class BadgesHandler {
  public HttpResponse handle(HttpRequest request) {
    ImUserInfoService service = Aop.get(ImUserInfoService.class);
    return ChatResp.ok(service.badges(LoginContext.uid()));
  }
}
