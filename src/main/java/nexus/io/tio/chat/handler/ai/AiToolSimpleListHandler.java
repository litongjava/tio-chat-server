package nexus.io.tio.chat.handler.ai;


import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiCrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/tool/simple-list */
public class AiToolSimpleListHandler {
  public HttpResponse handle(HttpRequest request) {
    AiCrudService crud = Aop.get(AiCrudService.class);
    return ChatResp.ok(crud.list("ai_tool", "status=0 order by id"));
  }
}
