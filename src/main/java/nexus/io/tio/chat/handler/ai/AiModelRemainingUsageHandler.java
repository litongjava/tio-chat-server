package nexus.io.tio.chat.handler.ai;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.ai.AiGenerateService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/model/get-remaining-usage */
public class AiModelRemainingUsageHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(AiGenerateService.class).remainingUsage(LoginContext.uid(), ParameterValidationUtils.requiredLong(request, "id")));
  }
}
