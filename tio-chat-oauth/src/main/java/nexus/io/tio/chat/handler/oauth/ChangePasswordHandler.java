package nexus.io.tio.chat.handler.oauth;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.service.oauth.RegisterService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/oauth/anyUser/updatePassword：路径不要求登录，这里必须是当前登录用户修改自己的密码 */
public class ChangePasswordHandler {
  public HttpResponse handle(HttpRequest request) {
    LoginSession session = LoginContext.get();
    if (session == null) {
      throw ChatErrors.of(ChatCode.UNAUTHORIZED, "请先登录");
    }
    Kv body = ParameterValidationUtils.bodyKv(request);
    return ChatResp.ok(Aop.get(RegisterService.class).changePassword(session.getUserId(), body.getStr("oldPassword"), body.getStr("newPassword"),
        body.getStr("confirmPassword")));
  }
}
