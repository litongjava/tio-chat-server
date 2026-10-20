package nexus.io.tio.chat.handler.ai;


import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiCrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/ai/api-key/create */
public class AiApiKeyCreateHandler {
  public HttpResponse handle(HttpRequest request) {
    AiCrudService crud = Aop.get(AiCrudService.class);
    Kv body = ParameterValidationUtils.bodyKv(request);
    // 未指定时为私有密钥，默认启用
    if (body.get("publicStatus") == null) {
      body.set("publicStatus", false);
    }
    if (body.get("status") == null) {
      body.set("status", 0);
    }
    return ChatResp.ok(crud.create("ai_api_key", body, true));
  }
}
