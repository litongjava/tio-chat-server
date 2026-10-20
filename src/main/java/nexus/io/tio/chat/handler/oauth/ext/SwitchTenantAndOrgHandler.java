package nexus.io.tio.chat.handler.oauth.ext;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.oauth.LoginService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.RequestUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/oauth/anyone/switchTenantAndOrg */
public class SwitchTenantAndOrgHandler {
  public HttpResponse handle(HttpRequest request) {
    Long orgId = ParameterValidationUtils.optionalLong(request, "orgId");
    if (orgId != null) {
      Db.update("update base_employee set last_dept_id=? where id=?", orgId, LoginContext.uid());
    }
    Kv user = Db.findFirstMap("select * from def_user where id=?", LoginContext.userId());
    return ChatResp.ok(Aop.get(LoginService.class).issue(user, LoginContext.required().getDeviceType(), request.getParam("clientId"),
        RequestUtils.ip(request)));
  }
}
