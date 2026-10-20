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

/** POST {path} 新增 */
public class CrudSaveHandler {
  private final CrudDef def;

  public CrudSaveHandler(CrudDef def) {
    this.def = def;
  }

  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    return ChatResp.ok(Aop.get(CrudService.class).save(def, body));
  }
}
