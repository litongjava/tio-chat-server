package nexus.io.tio.chat.handler.oauth.ext;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyone/findDeptByCompany */
public class FindDeptByCompanyHandler {
  public HttpResponse handle(HttpRequest request) {
    Long companyId = ParameterValidationUtils.optionalLong(request, "companyId");
    Long employeeId = ParameterValidationUtils.optionalLong(request, "employeeId");
    return ChatResp.ok(Db.findMaps("""
        select o.id, o.name, o.type_ as type, o.parent_id as parentId, o.tree_path as treePath from base_org o
        join base_employee_org_rel r on r.org_id=o.id
        where r.employee_id=? and r.is_del=0 and o.is_del=0 and o.type_='20' and (? is null or o.tree_path like ?)
        """, employeeId == null ? LoginContext.uid() : employeeId, companyId, "%/" + companyId + "/%"));
  }
}
