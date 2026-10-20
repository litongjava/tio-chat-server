package nexus.io.tio.chat.config.route;

import nexus.io.tio.chat.handler.base.crud.CrudCacheHandler;
import nexus.io.tio.chat.handler.base.crud.CrudCopyHandler;
import nexus.io.tio.chat.handler.base.crud.CrudDeleteHandler;
import nexus.io.tio.chat.handler.base.crud.CrudDetailHandler;
import nexus.io.tio.chat.handler.base.crud.CrudExportHandler;
import nexus.io.tio.chat.handler.base.crud.CrudFindByIdsHandler;
import nexus.io.tio.chat.handler.base.crud.CrudGetHandler;
import nexus.io.tio.chat.handler.base.crud.CrudPageHandler;
import nexus.io.tio.chat.handler.base.crud.CrudQueryHandler;
import nexus.io.tio.chat.handler.base.crud.CrudSaveHandler;
import nexus.io.tio.chat.handler.base.crud.CrudUpdateHandler;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * 为每个通用资源注册标准接口：新增、复制、修改、删除、单条、详情、查询、按 id 查询、分页
 */
public class CrudRouteRegistrar {

  public void register(HttpRequestRouter r, String prefix, CrudDef def) {
    String p = prefix + def.getPath();
    r.post(p, new CrudSaveHandler(def)::handle);
    r.post(p + "/copy", new CrudCopyHandler(def)::handle);
    r.put(p, new CrudUpdateHandler(def)::handle);
    r.delete(p, new CrudDeleteHandler(def)::handle);
    r.get(p + "/detail", new CrudDetailHandler(def)::handle);
    r.post(p + "/query", new CrudQueryHandler(def)::handle);
    r.post(p + "/findByIds", new CrudFindByIdsHandler(def)::handle);
    r.post(p + "/page", new CrudPageHandler(def)::handle);
    if (def.isCache()) {
      CrudCacheHandler cacheHandler = new CrudCacheHandler();
      r.post(p + "/refreshCache", cacheHandler::handle);
      r.post(p + "/clearCache", cacheHandler::handle);
    }
    if (def.isExcel()) {
      r.post(p + "/export", new CrudExportHandler(def)::handle);
    }
    r.get(p + "/{id}", new CrudGetHandler(def)::handle);
  }
}
