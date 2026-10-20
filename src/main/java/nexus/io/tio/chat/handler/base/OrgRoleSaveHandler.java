package nexus.io.tio.chat.handler.base;

import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseAdminService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/baseOrg/orgRole */
public class OrgRoleSaveHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    List<Long> roleIds = body.get("roleIdList") instanceof List ? Values.toLongList((List<?>) body.get("roleIdList")) : List.of();
    return ChatResp.ok(Aop.get(BaseAdminService.class).saveOrgRoles(Values.toLong(body.get("orgId")), roleIds, Values.toBool(body.get("flag"))));
  }
}
