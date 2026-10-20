package nexus.io.tio.chat.handler.im.user;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.ImUserInfoService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/im/user/badge */
public class WearingBadgeHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    ImUserInfoService service = Aop.get(ImUserInfoService.class);
    service.wearingBadge(LoginContext.uid(), Values.toLong(body.get("badgeId")));
    return ChatResp.ok();
  }
}
