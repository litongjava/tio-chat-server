import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Minimal WebSocket client for smoke tests.
 * Usage: java WsClient.java <url> <seconds> [message ...]
 * Prints every received frame prefixed with "RECV ".
 */
public class WsClient {
  public static void main(String[] args) throws Exception {
    String url = args[0];
    int seconds = Integer.parseInt(args[1]);
    CountDownLatch closed = new CountDownLatch(1);
    WebSocket.Listener listener = new WebSocket.Listener() {
      private final StringBuilder buffer = new StringBuilder();

      @Override
      public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        if (last) {
          System.out.println("RECV " + buffer);
          buffer.setLength(0);
        }
        webSocket.request(1);
        return null;
      }

      @Override
      public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("CLOSED " + statusCode + " " + reason);
        closed.countDown();
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public void onError(WebSocket webSocket, Throwable error) {
        System.out.println("ERROR " + error);
        closed.countDown();
      }
    };
    WebSocket ws;
    try {
      ws = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create(url), listener).join();
    } catch (Exception e) {
      System.out.println("CONNECT_FAILED " + e.getCause());
      return;
    }
    System.out.println("CONNECTED");
    for (int i = 2; i < args.length; i++) {
      ws.sendText(args[i], true).join();
      System.out.println("SENT " + args[i]);
    }
    closed.await(seconds, TimeUnit.SECONDS);
    ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    Thread.sleep(200);
  }
}
