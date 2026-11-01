package nexus.io.tio.chat.handler.im.user;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.BlackService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/user/black/remove */
public class RemoveBlackHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    Long id = Values.toLong(body.get("id"));
    ParameterValidationUtils.required(id, "id不能为空");
    BlackService service = Aop.get(BlackService.class);
    service.remove(id);
    return ChatResp.ok();
  }
}
