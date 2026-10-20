package nexus.io.tio.chat.handler.ai;

import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.ai.AiGenerateService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/ai/music/generate */
public class AiMusicGenerateHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    return ChatResp.ok(List.of(Aop.get(AiGenerateService.class).createTask("ai_music", LoginContext.uid(), body)));
  }
}
