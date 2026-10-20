package nexus.io.tio.chat.handler.ai;

import java.util.List;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiCrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/image/my-list-by-ids */
public class AiImageMyListByIdsHandler {
  public HttpResponse handle(HttpRequest request) {
    AiCrudService crud = Aop.get(AiCrudService.class);
    List<Long> ids = Values.toLongList(List.of(request.getParam("ids", "").split(",")));
    return ChatResp.ok(crud.listByIds("ai_image", ids, crud.uid()));
  }
}
