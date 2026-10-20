package nexus.io.tio.chat.handler.oauth.ext;

import java.util.ArrayList;
import java.util.List;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseExtService;
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
    return ChatResp.ok(Aop.get(BaseExtService.class).params(keys));
  }
}
