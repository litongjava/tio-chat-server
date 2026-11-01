package nexus.io.tio.chat.handler.im.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.ImUserInfoService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/user/search */
public class SearchUserHandler {
  public HttpResponse handle(HttpRequest request) {
    int pageNo = ParameterValidationUtils.optionalInt(request, "pageNo", 1);
    int pageSize = ParameterValidationUtils.optionalInt(request, "pageSize", 10);
    Long id = ParameterValidationUtils.optionalLong(request, "id");
    ImUserInfoService service = Aop.get(ImUserInfoService.class);
    return ChatResp.ok(service.search(id, request.getParam("keyword"), pageNo, pageSize));
  }
}
