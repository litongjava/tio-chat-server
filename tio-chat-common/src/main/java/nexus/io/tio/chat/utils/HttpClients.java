package nexus.io.tio.chat.utils;

import java.io.IOException;
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

  /** 不跟随跳转，否则公网地址可以跳转到内网 */
  private static final HttpClient NO_REDIRECT_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
      .followRedirects(HttpClient.Redirect.NEVER).build();

  /** 下载用户提交的地址：只允许公网地址 */
  public static String getPublic(String url) {
    PublicUrls.require(url);
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
      return NO_REDIRECT_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    } catch (Exception e) {
      throw ChatErrors.of("请求失败: " + e.getMessage());
    }
  }

  /** 上传到预签名地址，对象存储返回错误时抛出异常 */
  public static void putBytes(String url, byte[] data) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).PUT(HttpRequest.BodyPublishers.ofByteArray(data)).build();
      int status = CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
      ChatErrors.require(status >= 200 && status < 300, "文件保存失败：" + status);
    } catch (IOException | InterruptedException e) {
      throw ChatErrors.of("文件保存失败: " + e.getMessage());
    }
  }

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
