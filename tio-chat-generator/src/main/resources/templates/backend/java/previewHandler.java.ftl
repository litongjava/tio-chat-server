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
 * POST ${routePath}/preview：导出内容的 HTML 表格
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}PreviewHandler {

  public HttpResponse handle(HttpRequest request) {
    Kv body = JsonUtils.parse(request.getBodyString(), Kv.class);
    ${table.entityName} query = null;
    if (body != null && body.get("model") != null) {
      query = JsonUtils.parse(JsonUtils.toJson(body.get("model")), ${table.entityName}.class);
    }
    return TioRequestContext.getResponse().setJson(RespBodyVo.ok(Aop.get(${table.entityName}Service.class).exportHtml(query)));
  }
}
