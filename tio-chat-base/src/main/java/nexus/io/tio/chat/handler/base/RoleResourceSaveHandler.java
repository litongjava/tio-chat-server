package nexus.io.tio.chat.handler.base;

import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseAdminService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/baseRole/roleResource */
public class RoleResourceSaveHandler {
  @SuppressWarnings("unchecked")
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    Map<String, Object> map = body.get("applicationResourceMap") instanceof Map ? (Map<String, Object>) body.get("applicationResourceMap") : Map.of();
    return ChatResp.ok(Aop.get(BaseAdminService.class).saveRoleResources(Values.toLong(body.get("roleId")), map));
  }
}
