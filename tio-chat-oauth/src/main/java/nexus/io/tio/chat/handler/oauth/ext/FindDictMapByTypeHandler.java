package nexus.io.tio.chat.handler.oauth.ext;

import nexus.io.tio.chat.facade.Facades;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.json.JsonUtils;

/** POST /api/oauth/anyUser/dict/findDictMapByType */
public class FindDictMapByTypeHandler {
  @SuppressWarnings("unchecked")
  public HttpResponse handle(HttpRequest request) {
    List<Map<String, Object>> queries = new ArrayList<>();
    for (Object item : JsonUtils.parseArray(request.getBodyString() == null ? "[]" : request.getBodyString(), Object.class)) {
      if (item instanceof Map) {
        queries.add((Map<String, Object>) item);
      }
    }
    return ChatResp.ok(Facades.base().dictOptions(queries));
  }
}
