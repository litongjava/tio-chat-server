package nexus.io.tio.chat.handler.im.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.model.im.user.ModifyInfoReq;
import nexus.io.tio.chat.service.im.ImUserInfoService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/im/user/info */
public class ModifyInfoHandler {
  public HttpResponse handle(HttpRequest request) {
    ModifyInfoReq req = ParameterValidationUtils.parseBody(request, ModifyInfoReq.class);
    ImUserInfoService service = Aop.get(ImUserInfoService.class);
    service.modifyInfo(LoginContext.uid(), req);
    return ChatResp.ok();
  }
}
