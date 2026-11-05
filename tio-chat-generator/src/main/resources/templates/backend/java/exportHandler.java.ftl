package ${handlerPackage};

import java.nio.charset.StandardCharsets;

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
 * POST ${routePath}/export
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}ExportHandler {

  public HttpResponse handle(HttpRequest request) {
    Kv body = JsonUtils.parse(request.getBodyString(), Kv.class);
    ${table.entityName} query = null;
    if (body != null && body.get("model") != null) {
      query = JsonUtils.parse(JsonUtils.toJson(body.get("model")), ${table.entityName}.class);
    }
    HttpResponse response = TioRequestContext.getResponse();
    response.setContentType("application/octet-stream");
    response.addHeader("Content-Disposition", "attachment; filename=${table.entityName}.csv");
    response.setBody(Aop.get(${table.entityName}Service.class).exportCsv(query).getBytes(StandardCharsets.UTF_8));
    return response;
  }
}
