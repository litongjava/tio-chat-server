package nexus.io.tio.chat.handler.im.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.ImUserInfoService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/user/getById/{id} */
public class GetUserByIdHandler {
  public HttpResponse handle(HttpRequest request) {
    Long id = Values.toLong(request.getPathVar("id"));
    ParameterValidationUtils.required(id, "id不能为空");
    ImUserInfoService service = Aop.get(ImUserInfoService.class);
    return ChatResp.ok(service.getUserInfo(id));
  }
}
