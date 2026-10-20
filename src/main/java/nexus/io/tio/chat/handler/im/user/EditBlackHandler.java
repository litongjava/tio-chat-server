package nexus.io.tio.chat.handler.im.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.model.im.user.BlackEditReq;
import nexus.io.tio.chat.service.im.BlackService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/user/black/edit */
public class EditBlackHandler {
  public HttpResponse handle(HttpRequest request) {
    BlackEditReq req = ParameterValidationUtils.parseBody(request, BlackEditReq.class);
    ParameterValidationUtils.required(req.getId(), "id不能为空");
    BlackService service = Aop.get(BlackService.class);
    service.edit(req, LoginContext.uid());
    return ChatResp.ok();
  }
}
