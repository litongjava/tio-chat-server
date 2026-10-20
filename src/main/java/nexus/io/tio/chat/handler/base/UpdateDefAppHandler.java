package nexus.io.tio.chat.handler.base;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.base.BaseExtService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/anyone/updateDefApp */
public class UpdateDefAppHandler {
  public HttpResponse handle(HttpRequest request) {
    Long applicationId = ParameterValidationUtils.requiredLong(request, "applicationId");
    return ChatResp.ok(Aop.get(BaseExtService.class).updateDefApp(applicationId, LoginContext.userId()));
  }
}
