package nexus.io.tio.chat.handler.base;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.OnlineUserService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/defUser/onlineUsers/logout */
public class OnlineUserLogoutHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(OnlineUserService.class).logout(ParameterValidationUtils.optionalLong(request, "userId"), request.getParam("token")));
  }
}
