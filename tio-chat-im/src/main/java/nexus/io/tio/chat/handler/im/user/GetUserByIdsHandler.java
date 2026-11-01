package nexus.io.tio.chat.handler.im.user;

import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.ImUserInfoService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/user/getUserByIds */
public class GetUserByIdsHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    List<Long> uidList = Values.toLongList((List<?>) body.get("uidList"));
    ImUserInfoService service = Aop.get(ImUserInfoService.class);
    return ChatResp.ok(service.getUserByIds(uidList));
  }
}
