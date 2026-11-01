package nexus.io.tio.chat.handler.base.crud;

import java.util.List;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.chat.service.base.crud.CrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.json.JsonUtils;

/** POST {path}/findByIds 按 id 批量查询 */
public class CrudFindByIdsHandler {
  private final CrudDef def;

  public CrudFindByIdsHandler(CrudDef def) {
    this.def = def;
  }

  public HttpResponse handle(HttpRequest request) {
    List<Long> ids = parseIds(request.getBodyString());
    return ChatResp.ok(Aop.get(CrudService.class).findByIds(def, ids));
  }

  static List<Long> parseIds(String body) {
    if (body == null || body.isBlank()) {
      return List.of();
    }
    return Values.toLongList(JsonUtils.parseArray(body, Object.class));
  }
}
