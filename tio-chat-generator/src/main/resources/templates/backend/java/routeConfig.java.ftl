package ${routePackage};

import ${handlerPackage}.${table.entityName}DeleteHandler;
import ${handlerPackage}.${table.entityName}DetailHandler;
import ${handlerPackage}.${table.entityName}PageHandler;
import ${handlerPackage}.${table.entityName}SaveHandler;
import ${handlerPackage}.${table.entityName}UpdateHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * ${(table.swaggerComment!table.name)?replace("*/", "* /")}的接口
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}RouteConfig {

  public void config(HttpRequestRouter router) {
    router.post("${routePath}/page", new ${table.entityName}PageHandler()::handle);
    router.get("${routePath}/detail", new ${table.entityName}DetailHandler()::handle);
    router.post("${routePath}", new ${table.entityName}SaveHandler()::handle);
    router.put("${routePath}", new ${table.entityName}UpdateHandler()::handle);
    router.delete("${routePath}", new ${table.entityName}DeleteHandler()::handle);
  }
}
