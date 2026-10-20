package nexus.io.tio.chat.handler.ai;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiGenerateService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/ai/platform/add-model */
public class AiPlatformAddModelHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    return ChatResp.ok(Aop.get(AiGenerateService.class).addModelToPlatform(body.getStr("platform"), body.getStr("model")));
  }
}
