package nexus.io.tio.chat.handler.base.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.AdminUserService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/user/edit */
public class AdminUserEditHandler {
  public HttpResponse handle(HttpRequest request) {
    AdminUserService service = Aop.get(AdminUserService.class);
    return ChatResp.ok(service.edit(ParameterValidationUtils.bodyKv(request)));
  }
}
