package nexus.io.tio.chat.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 访问第三方 HTTP 接口
 */
public class HttpClients {
  private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
      .followRedirects(HttpClient.Redirect.NORMAL).build();

  public static String get(String url) {
    return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build());
  }

  public static String get(String url, Map<String, String> headers) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET();
    headers.forEach(builder::header);
    return send(builder.build());
  }

  public static byte[] getBytes(String url) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
      return CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
    } catch (Exception e) {
      throw ChatErrors.of("请求失败: " + e.getMessage());
    }
  }

  public static String post(String url, String contentType, String body, Map<String, String> headers) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
        .header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (headers != null) {
      headers.forEach(builder::header);
    }
    return send(builder.build());
  }

  private static String send(HttpRequest request) {
    try {
      return CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    } catch (Exception e) {
      throw ChatErrors.of("请求失败: " + e.getMessage());
    }
  }
}
