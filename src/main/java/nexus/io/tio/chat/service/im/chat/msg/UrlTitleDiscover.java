package nexus.io.tio.chat.service.im.chat.msg;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析文本中的链接标题，生成链接卡片信息
 */
public class UrlTitleDiscover {
  private static final Pattern URL = Pattern.compile("https?://[\\w.-]+(?::\\d+)?(?:/[^\\s<>\"']*)?");
  private static final Pattern TITLE = Pattern.compile("<title[^>]*>([^<]*)</title>", Pattern.CASE_INSENSITIVE);
  private static final Pattern DESCRIPTION = Pattern.compile(
      "<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
  private static final Pattern IMAGE = Pattern.compile(
      "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
  private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
      .followRedirects(HttpClient.Redirect.NORMAL).build();

  public static Map<String, Object> discover(String content) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (content == null || content.isEmpty()) {
      return result;
    }
    Matcher matcher = URL.matcher(content);
    int count = 0;
    while (matcher.find() && count < 3) {
      String url = matcher.group();
      count++;
      Map<String, Object> info = fetch(url);
      if (info != null) {
        result.put(url, info);
      }
    }
    return result;
  }

  private static Map<String, Object> fetch(String url) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3))
          .header("User-Agent", "Mozilla/5.0").GET().build();
      HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      String html = response.body();
      if (html == null) {
        return null;
      }
      Map<String, Object> info = new LinkedHashMap<>();
      info.put("title", find(TITLE, html));
      info.put("description", find(DESCRIPTION, html));
      info.put("image", find(IMAGE, html));
      return info.get("title") == null ? null : info;
    } catch (Exception e) {
      return null;
    }
  }

  private static String find(Pattern pattern, String html) {
    Matcher matcher = pattern.matcher(html);
    return matcher.find() ? matcher.group(1).trim() : null;
  }
}
