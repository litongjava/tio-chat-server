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
 * POST ${routePath}/page
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}PageHandler {

  public HttpResponse handle(HttpRequest request) {
    Kv body = JsonUtils.parse(request.getBodyString(), Kv.class);
    ${table.entityName} query = null;
    long current = 1;
    long size = 10;
    String sort = null;
    String order = null;
    if (body != null) {
      if (body.get("model") != null) {
        query = JsonUtils.parse(JsonUtils.toJson(body.get("model")), ${table.entityName}.class);
      }
      if (body.get("current") != null) {
        current = Long.parseLong(body.get("current").toString());
      }
      if (body.get("size") != null) {
        size = Long.parseLong(body.get("size").toString());
      }
      sort = body.getStr("sort");
      order = body.getStr("order");
    }
    Kv page = Aop.get(${table.entityName}Service.class).page(query, current, size, sort, order);
    return TioRequestContext.getResponse().setJson(RespBodyVo.ok(page));
  }
}
