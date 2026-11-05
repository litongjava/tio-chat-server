package ${handlerPackage};

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import ${servicePackage}.${table.entityName}Service;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * POST ${routePath}/copy
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}CopyHandler {

  public HttpResponse handle(HttpRequest request) {
    String id = request.getParam("id");
    if (id == null || id.isEmpty()) {
      Kv body = JsonUtils.parse(request.getBodyString(), Kv.class);
      id = body == null || body.get("id") == null ? null : body.get("id").toString();
    }
    if (id == null || id.isEmpty()) {
      return TioRequestContext.getResponse().setJson(RespBodyVo.fail("id不能为空"));
    }
    Kv copy = Aop.get(${table.entityName}Service.class).copy(id);
    if (copy == null) {
      return TioRequestContext.getResponse().setJson(RespBodyVo.fail("数据不存在或已被删除，请刷新重试"));
    }
    return TioRequestContext.getResponse().setJson(RespBodyVo.ok(copy));
  }
}
