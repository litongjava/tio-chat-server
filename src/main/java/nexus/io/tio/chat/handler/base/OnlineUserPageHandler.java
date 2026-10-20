package nexus.io.tio.chat.handler.base;

import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.OnlineUserService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/defUser/onlineUsers/page */
public class OnlineUserPageHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    Map<?, ?> model = body.get("model") instanceof Map ? (Map<?, ?>) body.get("model") : Map.of();
    long size = Values.toLong(body.get("size")) == null ? 10 : Values.toLong(body.get("size"));
    long current = Values.toLong(body.get("current")) == null ? 1 : Values.toLong(body.get("current"));
    Object username = model.get("username");
    Object nickName = model.get("nickName");
    return ChatResp.ok(Aop.get(OnlineUserService.class).page(username == null ? null : username.toString(),
        nickName == null ? null : nickName.toString(), size, current));
  }
}
