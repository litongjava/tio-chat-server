package ${handlerPackage};

import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import ${servicePackage}.${table.entityName}Service;

/**
 * GET ${routePath}/detail
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}DetailHandler {

  public HttpResponse handle(HttpRequest request) {
    String id = request.getParam("id");
    if (id == null || id.isEmpty()) {
      return TioRequestContext.getResponse().setJson(RespBodyVo.fail("id不能为空"));
    }
    return TioRequestContext.getResponse().setJson(RespBodyVo.ok(Aop.get(${table.entityName}Service.class).detail(id)));
  }
}
