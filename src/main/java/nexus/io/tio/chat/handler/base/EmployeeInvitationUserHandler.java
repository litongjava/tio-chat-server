package nexus.io.tio.chat.handler.base;

import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseExtService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/baseEmployee/invitationUser */
public class EmployeeInvitationUserHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    List<Long> userIds = body.get("userIdList") instanceof List ? Values.toLongList((List<?>) body.get("userIdList")) : List.of();
    return ChatResp.ok(Aop.get(BaseExtService.class).bindTenantUsers(Values.toLong(body.get("tenantId")), userIds, Values.isTrue(body.get("isBind"))));
  }
}
