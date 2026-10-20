package nexus.io.tio.chat.handler.ai;

import java.util.List;

import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/audio/voices */
public class AiAudioVoicesHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(List.of("alloy", "echo", "fable", "onyx", "nova", "shimmer"));
  }
}
