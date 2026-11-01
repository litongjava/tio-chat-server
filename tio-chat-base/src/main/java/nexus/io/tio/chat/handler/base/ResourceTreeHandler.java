package nexus.io.tio.chat.handler.base;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseAdminService;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/defResource/tree */
public class ResourceTreeHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    CrudDef def = CrudDef.of("/defResource", "def_resource").echo("resourceType", "dict:TENANT_RESOURCE_TYPE")
        .echo("openWith", "dict:TENANT_RESOURCE_OPEN_WITH").echo("dataScope", "dict:TENANT_RESOURCE_DATA_SCOPE");
    return ChatResp.ok(Aop.get(BaseAdminService.class).tree(def, body));
  }
}
