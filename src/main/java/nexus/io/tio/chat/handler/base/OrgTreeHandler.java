package nexus.io.tio.chat.handler.base;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseAdminService;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/baseOrg/tree */
public class OrgTreeHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    CrudDef def = CrudDef.of("/baseOrg", "base_org").like("name").echo("type", "dict:BASE_ORG_TYPE");
    return ChatResp.ok(Aop.get(BaseAdminService.class).tree(def, body));
  }
}
