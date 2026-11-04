package nexus.io.tio.chat.handler.im.system;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.ImUserService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/user/register */
public class UserRegisterHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(ImUserService.class).registerFromOauth(ParameterValidationUtils.bodyKv(request)));
  }
}
