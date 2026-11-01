package nexus.io.tio.chat.handler.im.misc;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.ImExtraService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/user/state/changeState */
public class UserStateChangeHandler {
  public HttpResponse handle(HttpRequest request) {
    Long id = ParameterValidationUtils.optionalLong(request, "id");
    if (id == null) {
      id = Values.toLong(ParameterValidationUtils.bodyKv(request).get("id"));
    }
    ParameterValidationUtils.required(id, "id不能为空");
    ImExtraService service = Aop.get(ImExtraService.class);
    return ChatResp.ok(service.changeState(LoginContext.uid(), id));
  }
}
