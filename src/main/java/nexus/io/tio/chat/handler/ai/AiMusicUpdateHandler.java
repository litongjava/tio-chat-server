package nexus.io.tio.chat.handler.ai;


import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.ai.AiCrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/ai/music/update */
public class AiMusicUpdateHandler {
  public HttpResponse handle(HttpRequest request) {
    AiCrudService crud = Aop.get(AiCrudService.class);
    Kv body = ParameterValidationUtils.bodyKv(request);
    return ChatResp.ok(crud.update("ai_music", body, null));
  }
}
