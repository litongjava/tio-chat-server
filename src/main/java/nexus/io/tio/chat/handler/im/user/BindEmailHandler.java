package nexus.io.tio.chat.handler.im.user;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.ImUserInfoService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/user/bindEmail */
public class BindEmailHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    ParameterValidationUtils.notBlank(body.getStr("email"), "邮箱不能为空");
    ImUserInfoService service = Aop.get(ImUserInfoService.class);
    String key = body.getStr("templateCode");
    return ChatResp.ok(service.bindEmail(LoginContext.uid(), body.getStr("email"), key, body.getStr("code")));
  }
}
