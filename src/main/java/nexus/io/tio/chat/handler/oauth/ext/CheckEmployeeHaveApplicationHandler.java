package nexus.io.tio.chat.handler.oauth.ext;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyone/checkEmployeeHaveApplication */
public class CheckEmployeeHaveApplicationHandler {
  public HttpResponse handle(HttpRequest request) {
    Long applicationId = ParameterValidationUtils.requiredLong(request, "applicationId");
    Long count = Db.queryLong("""
        select count(*) from def_application a left join def_tenant_application_rel r on r.application_id=a.id
        where a.id=? and a.is_del=0 and (a.is_general=1 or r.id is not null)
        """, applicationId);
    return ChatResp.ok(count > 0);
  }
}
