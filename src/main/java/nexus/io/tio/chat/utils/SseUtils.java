package nexus.io.tio.chat.utils;

import java.nio.charset.StandardCharsets;

import nexus.io.http.common.sse.SsePacket;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.Tio;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.http.server.util.SseEmitter;

/**
 * Server-Sent Events 输出：先发送响应头，再逐条发送事件，最后关闭连接
 */
public class SseUtils {

  public static ChannelContext open(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    response.addServerSentEventsHeader();
    ChannelContext ctx = request.getChannelContext();
    Tio.bSend(ctx, response);
    response.setSend(false);
    return ctx;
  }

  public static void send(ChannelContext ctx, String event, String data) {
    Tio.bSend(ctx, new SsePacket(event, data.getBytes(StandardCharsets.UTF_8)));
  }

  public static void send(ChannelContext ctx, String data) {
    Tio.bSend(ctx, new SsePacket(data.getBytes(StandardCharsets.UTF_8)));
  }

  public static void close(ChannelContext ctx) {
    SseEmitter.closeSeeConnection(ctx);
  }
}
