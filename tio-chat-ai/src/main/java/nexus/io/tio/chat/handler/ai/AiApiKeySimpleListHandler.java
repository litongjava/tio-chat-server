package nexus.io.tio.chat.handler.ai;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiCrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/api-key/simple-list */
public class AiApiKeySimpleListHandler {
  public HttpResponse handle(HttpRequest request) {
    AiCrudService crud = Aop.get(AiCrudService.class);
    // 公开密钥与自己的密钥，只返回编号、名称和平台
    List<Kv> list = new ArrayList<>();
    for (Kv row : crud.list("ai_api_key", "status=0 and (public_status=1 or user_id=?) order by id", crud.uid())) {
      list.add(Kv.by("id", row.get("id")).set("name", row.get("name")).set("platform", row.get("platform")));
    }
    return ChatResp.ok(list);
  }
}
