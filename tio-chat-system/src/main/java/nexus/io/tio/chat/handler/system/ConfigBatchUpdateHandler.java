package nexus.io.tio.chat.handler.system;

import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.system.SystemService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.json.JsonUtils;

/** PUT /api/system/anyTenant/config/batchUpdate */
public class ConfigBatchUpdateHandler {
  public HttpResponse handle(HttpRequest request) {
    LoginContext.required();
    String body = request.getBodyString();
    SystemService service = Aop.get(SystemService.class);
    if (body != null && !body.isBlank()) {
      for (Object item : JsonUtils.parseArray(body, Object.class)) {
        if (item instanceof Map) {
          service.updateConfig(Kv.create().set((Map<?, ?>) item));
        }
      }
    }
    return ChatResp.ok(true);
  }
}
