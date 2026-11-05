package ${routePackage};

<#if canRead>
import ${handlerPackage}.${table.entityName}DetailHandler;
import ${handlerPackage}.${table.entityName}FindByIdsHandler;
import ${handlerPackage}.${table.entityName}GetHandler;
import ${handlerPackage}.${table.entityName}PageHandler;
import ${handlerPackage}.${table.entityName}QueryHandler;
</#if>
<#if canWrite>
import ${handlerPackage}.${table.entityName}CopyHandler;
import ${handlerPackage}.${table.entityName}DeleteHandler;
import ${handlerPackage}.${table.entityName}SaveHandler;
import ${handlerPackage}.${table.entityName}UpdateHandler;
</#if>
<#if canCache>
import ${handlerPackage}.${table.entityName}CacheHandler;
</#if>
<#if canExport>
import ${handlerPackage}.${table.entityName}ExportHandler;
</#if>
<#if hasTree>
import ${handlerPackage}.${table.entityName}TreeHandler;
</#if>
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * ${(table.swaggerComment!table.name)?replace("*/", "* /")}的接口
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}RouteConfig {

  public void config(HttpRequestRouter router) {
<#if canRead>
    router.post("${routePath}/page", new ${table.entityName}PageHandler()::handle);
    router.post("${routePath}/query", new ${table.entityName}QueryHandler()::handle);
    router.post("${routePath}/findByIds", new ${table.entityName}FindByIdsHandler()::handle);
    router.get("${routePath}/detail", new ${table.entityName}DetailHandler()::handle);
    router.get("${routePath}/{id}", new ${table.entityName}GetHandler()::handle);
</#if>
<#if canWrite>
    router.post("${routePath}", new ${table.entityName}SaveHandler()::handle);
    router.post("${routePath}/copy", new ${table.entityName}CopyHandler()::handle);
    router.put("${routePath}", new ${table.entityName}UpdateHandler()::handle);
    router.delete("${routePath}", new ${table.entityName}DeleteHandler()::handle);
</#if>
<#if canCache>
    router.post("${routePath}/refreshCache", new ${table.entityName}CacheHandler()::handle);
    router.post("${routePath}/clearCache", new ${table.entityName}CacheHandler()::handle);
</#if>
<#if canExport>
    router.post("${routePath}/export", new ${table.entityName}ExportHandler()::handle);
</#if>
<#if hasTree>
    router.post("${routePath}/tree", new ${table.entityName}TreeHandler()::handle);
</#if>
<#if mainSub>
    new ${sub.table.entityName}RouteConfig().config(router);
</#if>
  }
}
