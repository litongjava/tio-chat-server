package ${handlerPackage};


import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import ${servicePackage}.${table.entityName}Service;

/**
 * POST ${routePath}/refreshCache、${routePath}/clearCache
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}CacheHandler {

  public HttpResponse handle(HttpRequest request) {
    Aop.get(${table.entityName}Service.class).clearCache();
    return TioRequestContext.getResponse().setJson(RespBodyVo.ok(true));
  }
}
