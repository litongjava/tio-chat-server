package nexus.io.tio.chat.handler.base;

import java.util.HashMap;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.facade.Facades;
import nexus.io.tio.chat.config.route.BaseCrudRegistry;
import nexus.io.tio.chat.service.base.crud.CrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/baseLoginLog/anyone/page */
public class BaseLoginLogAnyonePageHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    if (!Facades.base().isAdmin(LoginContext.uid())) {
      // 普通用户只能看到自己的登录日志，日志里有 IP 和设备信息
      Map<String, Object> model = new HashMap<>();
      if (body.get("model") instanceof Map<?, ?> given) {
        given.forEach((key, value) -> model.put(String.valueOf(key), value));
      }
      model.put("employeeId", LoginContext.uid());
      body.set("model", model);
    }
    return ChatResp.ok(Aop.get(CrudService.class).page(BaseCrudRegistry.find("/baseLoginLog"), body));
  }
}
