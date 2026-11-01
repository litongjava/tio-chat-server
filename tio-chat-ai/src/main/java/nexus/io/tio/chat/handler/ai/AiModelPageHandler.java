package nexus.io.tio.chat.handler.ai;

import java.util.Set;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiCrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.RequestParams;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/model/page */
public class AiModelPageHandler {
  public HttpResponse handle(HttpRequest request) {
    AiCrudService crud = Aop.get(AiCrudService.class);
    // 公开模型（public_status=0）与自己的私有模型
    return ChatResp.ok(crud.page("ai_model", RequestParams.toKv(request), Set.of("name", "model"), "(public_status=0 or user_id=?)", crud.uid()));
  }
}
