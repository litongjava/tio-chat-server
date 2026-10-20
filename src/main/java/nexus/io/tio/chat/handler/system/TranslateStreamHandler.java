package nexus.io.tio.chat.handler.system;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.chat.service.system.TranslateService;
import nexus.io.tio.chat.utils.SseUtils;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/system/anyTenant/translate/stream */
public class TranslateStreamHandler {
  public HttpResponse handle(HttpRequest request) {
    TranslateService service = Aop.get(TranslateService.class);
    ChannelContext ctx = SseUtils.open(request);
    try {
      String provider = service.resolveProvider(request.getParam("provider"));
      for (String segment : service.segments(request.getParam("text"), provider, request.getParam("source", "auto"), request.getParam("target"))) {
        SseUtils.send(ctx, "segment", segment);
      }
      SseUtils.send(ctx, "end", "done");
    } catch (Exception e) {
      SseUtils.send(ctx, "error", e.getMessage() == null ? "error" : e.getMessage());
    } finally {
      SseUtils.close(ctx);
    }
    return TioRequestContext.getResponse();
  }
}
