package nexus.io.tio.chat.handler.base;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/base/baseOrg/findDeptByEmployeeId */
public class OrgDeptByEmployeeHandler {
  public HttpResponse handle(HttpRequest request) {
    Long employeeId = ParameterValidationUtils.requiredLong(request, "employeeId");
    Long companyId = ParameterValidationUtils.optionalLong(request, "companyId");
    String sql = """
        select o.id, o.name, o.type_ as type, o.short_name as shortName, o.parent_id as parentId, o.tree_path as treePath
        from base_org o join base_employee_org_rel r on r.org_id=o.id
        where r.employee_id=? and r.is_del=0 and o.is_del=0 and o.type_='20' and (? is null or o.tree_path like ?)
        """;
    return ChatResp.ok(Db.findMaps(sql, employeeId, companyId, "%/" + companyId + "/%"));
  }
}
