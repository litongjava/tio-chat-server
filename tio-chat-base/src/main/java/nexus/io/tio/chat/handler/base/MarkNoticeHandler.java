package nexus.io.tio.chat.handler.base;

import java.util.List;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseExtService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.json.JsonUtils;

/** POST /api/base/anyone/extendNotice/mark */
public class MarkNoticeHandler {
  public HttpResponse handle(HttpRequest request) {
    List<Long> ids = request.getBodyString() == null || request.getBodyString().isBlank() ? List.of() : Values.toLongList(JsonUtils.parseArray(request.getBodyString(), Object.class));
    return ChatResp.ok(Aop.get(BaseExtService.class).markNotice(ids));
  }
}
