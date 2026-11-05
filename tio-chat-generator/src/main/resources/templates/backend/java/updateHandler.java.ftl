package ${handlerPackage};

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import ${modelPackage}.${table.entityName};
import ${servicePackage}.${table.entityName}Service;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * PUT ${routePath}
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}UpdateHandler {

  public HttpResponse handle(HttpRequest request) {
    ${table.entityName} model = JsonUtils.parse(request.getBodyString(), ${table.entityName}.class);
    if (model == null || model.get${pkField.javaField?cap_first}() == null) {
      return TioRequestContext.getResponse().setJson(RespBodyVo.fail("id不能为空"));
    }
    Kv updated = Aop.get(${table.entityName}Service.class).update(model);
    if (updated == null) {
      return TioRequestContext.getResponse().setJson(RespBodyVo.fail("数据不存在或已被删除，请刷新重试"));
    }
    return TioRequestContext.getResponse().setJson(RespBodyVo.ok(updated));
  }
}
