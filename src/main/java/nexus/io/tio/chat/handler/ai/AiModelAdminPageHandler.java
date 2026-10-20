package nexus.io.tio.chat.handler.ai;

import java.util.Set;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiCrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.RequestParams;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/model/admin/page */
public class AiModelAdminPageHandler {
  public HttpResponse handle(HttpRequest request) {
    AiCrudService crud = Aop.get(AiCrudService.class);
    return ChatResp.ok(crud.page("ai_model", RequestParams.toKv(request), Set.of("name", "model"), null));
  }
}
