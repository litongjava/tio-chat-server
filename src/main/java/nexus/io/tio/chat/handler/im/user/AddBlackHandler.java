package nexus.io.tio.chat.handler.im.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.model.im.user.BlackAddReq;
import nexus.io.tio.chat.service.im.BlackService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/im/user/black */
public class AddBlackHandler {
  public HttpResponse handle(HttpRequest request) {
    BlackAddReq req = ParameterValidationUtils.parseBody(request, BlackAddReq.class);
    BlackService service = Aop.get(BlackService.class);
    service.add(req, LoginContext.uid());
    return ChatResp.ok();
  }
}
