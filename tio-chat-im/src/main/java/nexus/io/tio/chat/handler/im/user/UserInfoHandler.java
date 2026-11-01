package nexus.io.tio.chat.handler.im.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.ImUserInfoService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/user/userInfo */
public class UserInfoHandler {
  public HttpResponse handle(HttpRequest request) {
    ImUserInfoService service = Aop.get(ImUserInfoService.class);
    return ChatResp.ok(service.getUserInfo(LoginContext.uid()));
  }
}
