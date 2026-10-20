package nexus.io.tio.chat.handler.im.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.ImUserService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/user/checkEmail */
public class CheckImEmailHandler {
  public HttpResponse handle(HttpRequest request) {
    String email = request.getParam("email");
    ParameterValidationUtils.notBlank(email, "邮箱不能为空");
    ImUserService service = Aop.get(ImUserService.class);
    return ChatResp.ok(service.existsEmail(email));
  }
}
