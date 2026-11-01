package nexus.io.tio.chat.utils;

import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.core.Tio;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.http.server.util.SseEmitter;

/**
 * Server-Sent Events 输出：先发送响应头，再逐条发送事件（LF 分行），最后关闭连接
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
    Tio.bSend(ctx, new LfSsePacket(event, data));
  }

  /**
   * 发送一条事件，返回 false 表示连接已关闭（客户端断开）
   */
  public static boolean send(ChannelContext ctx, String data) {
    if (ctx.isClosed) {
      return false;
    }
    return Tio.bSend(ctx, new LfSsePacket(null, data));
  }

  public static void close(ChannelContext ctx) {
    SseEmitter.closeSeeConnection(ctx);
  }
}
