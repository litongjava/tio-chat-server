package nexus.io.tio.chat.handler.oauth.ext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.oauth.EnumOptionService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.json.JsonUtils;

/** POST /api/oauth/anyTenant/enums/findEnumMapByType */
public class FindEnumMapByTypeHandler {
  public HttpResponse handle(HttpRequest request) {
    List<String> types = new ArrayList<>();
    for (Object item : JsonUtils.parseArray(request.getBodyString() == null ? "[]" : request.getBodyString(), Object.class)) {
      types.add(item instanceof Map ? String.valueOf(((Map<?, ?>) item).get("type")) : String.valueOf(item));
    }
    return ChatResp.ok(Aop.get(EnumOptionService.class).findMap(types));
  }
}
