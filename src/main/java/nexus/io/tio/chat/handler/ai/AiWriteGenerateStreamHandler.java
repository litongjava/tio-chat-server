package nexus.io.tio.chat.handler.ai;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.ai.AiChatService;
import nexus.io.tio.chat.service.ai.AiGenerateService;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.SseUtils;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.thread.TioThreadUtils;

/** POST /api/ai/write/generate-stream */
public class AiWriteGenerateStreamHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    long uid = LoginContext.uid();
    ChannelContext ctx = SseUtils.open(request);
    TioThreadUtils.execute(() -> {
      try {
        Aop.get(AiGenerateService.class).writeStream(ctx, uid, body);
      } catch (Exception e) {
        AiChatService.error(ctx, e.getMessage());
      }
    });
    return TioRequestContext.getResponse();
  }
}
