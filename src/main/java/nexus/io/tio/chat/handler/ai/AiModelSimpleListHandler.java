package nexus.io.tio.chat.handler.ai;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiGenerateService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/model/simple-list */
public class AiModelSimpleListHandler {
  public HttpResponse handle(HttpRequest request) {
    Integer type = ParameterValidationUtils.optionalLong(request, "type") == null ? null : ParameterValidationUtils.optionalInt(request, "type", 1);
    return ChatResp.ok(Aop.get(AiGenerateService.class).modelSimpleList(type, request.getParam("platform")));
  }
}
