package nexus.io.tio.chat.handler.system;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.system.SystemService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/system/anyTenant/config/update */
public class ConfigUpdateHandler {
  public HttpResponse handle(HttpRequest request) {
    LoginContext.required();
    Kv body = ParameterValidationUtils.bodyKv(request);
    SystemService service = Aop.get(SystemService.class);
    return ChatResp.ok(service.updateConfig(body));
  }
}
