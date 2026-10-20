package nexus.io.tio.chat.handler.im.user;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.ImUserInfoService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/user/avatar */
public class ModifyAvatarHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    ImUserInfoService service = Aop.get(ImUserInfoService.class);
    service.modifyAvatar(LoginContext.uid(), body.getStr("avatar"));
    return ChatResp.ok();
  }
}
