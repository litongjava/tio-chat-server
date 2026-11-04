package ${handlerPackage};

import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import ${modelPackage}.${table.entityName};
import ${servicePackage}.${table.entityName}Service;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * POST ${routePath}
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}SaveHandler {

  public HttpResponse handle(HttpRequest request) {
    ${table.entityName} model = JsonUtils.parse(request.getBodyString(), ${table.entityName}.class);
    if (model == null) {
      return TioRequestContext.getResponse().setJson(RespBodyVo.fail("请求内容不能为空"));
    }
    return TioRequestContext.getResponse().setJson(RespBodyVo.ok(Aop.get(${table.entityName}Service.class).save(model)));
  }
}
