import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

/**
 * Minimal OpenAI-compatible chat completion server for smoke tests.
 * Usage: java MockLlm.java <port>
 */
public class MockLlm {
  /**
   * Answers depend on the last question, so client tests can assert real behaviour: a capital city question gets
   * an answer naming Beijing, thinking requests get reasoning first, an essay request streams slowly until stopped.
   */
  private static void streamAnswer(String body, OutputStream out) throws IOException {
    int lastUser = body.lastIndexOf("\"role\":\"user\"");
    // the message object holding the last user role, whatever the order of its keys
    String question = lastUser < 0 ? "" : body.substring(body.lastIndexOf('{', lastUser), body.indexOf('}', lastUser));
    if (body.matches("(?s).*\"thinking\"\\s*:\\s*\\{\\s*\"type\"\\s*:\\s*\"enabled\".*")) {
      for (String word : new String[] { "先比较", "整数部分", "，再比较", "小数部分" }) {
        chunk(out, "reasoning_content", word);
      }
    }
    if (question.contains("四大发明")) {
      for (int i = 0; i < 600; i++) {
        chunk(out, "content", "造纸术、印刷术、火药和指南针影响深远。");
        pause(100);
      }
      return;
    }
    String[] words = new String[] { "你好", "，", "我是", "测试模型" };
    if (question.contains("首都")) {
      words = new String[] { "中国的首都是", "北京", "，著名景点有", "故宫。" };
    } else if (question.contains("9.11")) {
      words = new String[] { "9.9", " 比 ", "9.11", " 大。" };
    }
    for (String word : words) {
      chunk(out, "content", word);
    }
  }

  private static void chunk(OutputStream out, String field, String text) throws IOException {
    String data = "{\"choices\":[{\"delta\":{\"" + field + "\":\"" + text + "\"}}]}";
    out.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
    out.flush();
  }

  private static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

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
        try {
          streamAnswer(body, out);
        } catch (IOException clientGone) {
          // the caller stopped the answer
          exchange.close();
          return;
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
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    System.out.println("mock llm on " + port);
  }
}
