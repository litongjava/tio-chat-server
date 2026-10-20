package nexus.io.tio.chat.handler.base;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseAdminService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** DELETE /api/base/defLoginLog/clear */
public class DefLoginLogClearHandler {
  public HttpResponse handle(HttpRequest request) {
    int type = ParameterValidationUtils.optionalInt(request, "type", 1);
    return ChatResp.ok(Aop.get(BaseAdminService.class).clearLog("def_login_log", type));
  }
}
