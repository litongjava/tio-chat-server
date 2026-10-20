package nexus.io.tio.chat.handler.base;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseAdminService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/base/defDictItem/check */
public class DictItemCheckHandler {
  public HttpResponse handle(HttpRequest request) {
    Long id = ParameterValidationUtils.optionalLong(request, "id");
    BaseAdminService service = Aop.get(BaseAdminService.class);
    return ChatResp.ok(service.exists("def_dict", "key_", request.getParam("key"), id, "parent_id", ParameterValidationUtils.optionalLong(request, "dictId")));
  }
}
