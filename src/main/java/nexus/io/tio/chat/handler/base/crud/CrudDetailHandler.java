package nexus.io.tio.chat.handler.base.crud;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.chat.service.base.crud.CrudService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET {path}/detail 单条查询并回显 */
public class CrudDetailHandler {
  private final CrudDef def;

  public CrudDetailHandler(CrudDef def) {
    this.def = def;
  }

  public HttpResponse handle(HttpRequest request) {
    Long id = ParameterValidationUtils.requiredLong(request, "id");
    return ChatResp.ok(Aop.get(CrudService.class).detail(def, id));
  }
}
