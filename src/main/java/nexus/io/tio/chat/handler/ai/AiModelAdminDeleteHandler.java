package nexus.io.tio.chat.handler.ai;


import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiCrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** DELETE /api/ai/model/admin/delete */
public class AiModelAdminDeleteHandler {
  public HttpResponse handle(HttpRequest request) {
    AiCrudService crud = Aop.get(AiCrudService.class);
    Long id = ParameterValidationUtils.requiredLong(request, "id");
    return ChatResp.ok(crud.delete("ai_model", id, null));
  }
}
