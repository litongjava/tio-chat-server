package nexus.io.tio.chat.handler.base;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.config.route.BaseCrudRegistry;
import nexus.io.tio.chat.service.base.crud.CrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/baseRole/pageMyRole */
public class RolePageMyRoleHandler {
  @SuppressWarnings("unchecked")
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    Map<String, Object> model = body.get("model") instanceof Map ? new HashMap<>((Map<String, Object>) body.get("model")) : new HashMap<>();
    Map<String, Object> extra = body.get("extra") instanceof Map ? new HashMap<>((Map<String, Object>) body.get("extra")) : new HashMap<>();
    String scopeType = (String) model.remove("scopeType");
    String scope = (String) model.remove("scope");
    Long employeeId = Values.toLong(model.remove("employeeId"));
    Long orgId = Values.toLong(model.remove("orgId"));
    List<Long> boundIds = null;
    if ("1".equals(scopeType) && employeeId != null) {
      boundIds = Db.query("select role_id from base_employee_role_rel where employee_id=? and is_del=0", employeeId);
    } else if ("2".equals(scopeType) && orgId != null) {
      boundIds = Db.query("select role_id from base_org_role_rel where org_id=? and is_del=0", orgId);
    }
    if (boundIds != null && "1".equals(scope)) {
      extra.put("id_in", boundIds.isEmpty() ? List.of(-1L) : boundIds);
    } else if (boundIds != null && "2".equals(scope) && !boundIds.isEmpty()) {
      extra.put("id_notIn", boundIds);
    }
    body.set("model", model).set("extra", extra);
    return ChatResp.ok(Aop.get(CrudService.class).page(BaseCrudRegistry.find("/baseRole"), body));
  }
}
