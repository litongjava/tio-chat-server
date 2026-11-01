package nexus.io.tio.chat.handler.oauth.ext;

import nexus.io.tio.chat.facade.Facades;
import java.util.ArrayList;
import java.util.List;

import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.json.JsonUtils;

/** POST /api/oauth/anyUser/parameter/findParamMapByKey */
public class FindParamMapByKeyHandler {
  public HttpResponse handle(HttpRequest request) {
    List<String> keys = new ArrayList<>();
    for (Object item : JsonUtils.parseArray(request.getBodyString() == null ? "[]" : request.getBodyString(), Object.class)) {
      keys.add(String.valueOf(item));
    }
    return ChatResp.ok(Facades.base().params(keys));
  }
}
