package nexus.io.tio.chat.handler.base;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseAdminService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/base/defUser/resetPassword */
public class UserResetPasswordHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    return ChatResp.ok(Aop.get(BaseAdminService.class).resetPassword(Values.toLong(body.get("id")), Values.toBool(body.get("isUseSystemPassword")),
        body.getStr("password"), body.getStr("confirmPassword")));
  }
}
