import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;

/**
 * Minimal OpenAI-compatible chat completion server for smoke tests.
 * Usage: java MockLlm.java <port>
 */
public class MockLlm {
  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(args[0]);
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      boolean stream = body.contains("\"stream\":true");
      OutputStream out = exchange.getResponseBody();
      if (stream) {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        for (String word : new String[] { "你好", "，", "我是", "测试模型" }) {
          String chunk = "{\"choices\":[{\"delta\":{\"content\":\"" + word + "\"}}]}";
          out.write(("data: " + chunk + "\n\n").getBytes(StandardCharsets.UTF_8));
          out.flush();
        }
        out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
      } else {
        byte[] resp = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"你好，我是测试模型\"}}]}"
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, resp.length);
        out.write(resp);
      }
      out.close();
    });
    server.start();
    System.out.println("mock llm on " + port);
  }
}
