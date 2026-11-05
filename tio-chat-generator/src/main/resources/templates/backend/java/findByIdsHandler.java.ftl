package ${handlerPackage};

import java.util.List;

import com.alibaba.fastjson2.JSON;

import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import ${servicePackage}.${table.entityName}Service;

/**
 * POST ${routePath}/findByIds
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}FindByIdsHandler {

  public HttpResponse handle(HttpRequest request) {
    List<${pkField.javaType}> ids = JSON.parseArray(request.getBodyString(), ${pkField.javaType}.class);
    return TioRequestContext.getResponse().setJson(RespBodyVo.ok(Aop.get(${table.entityName}Service.class).findByIds(ids)));
  }
}
