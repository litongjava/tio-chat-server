package nexus.io.tio.chat.handler.oauth.ext;

import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyone/findCompanyDept */
public class FindCompanyDeptHandler {
  public HttpResponse handle(HttpRequest request) {
    long employeeId = LoginContext.uid();
    Kv employee = Db.findFirstMap("select last_company_id, last_dept_id from base_employee where id=?", employeeId);
    List<Kv> orgs = Db.findMaps("""
        select o.id, o.name, o.type_ as type, o.parent_id as parentId, o.tree_path as treePath from base_org o
        join base_employee_org_rel r on r.org_id=o.id where r.employee_id=? and r.is_del=0 and o.is_del=0
        """, employeeId);
    return ChatResp.ok(Kv.by("orgList", orgs).set("employeeId", employeeId)
        .set("currentCompanyId", employee == null ? null : employee.getLong("last_company_id"))
        .set("currentDeptId", employee == null ? null : employee.getLong("last_dept_id")));
  }
}
