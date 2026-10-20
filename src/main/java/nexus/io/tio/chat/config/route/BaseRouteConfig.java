package nexus.io.tio.chat.config.route;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * 后台管理接口：先注册各资源的扩展接口，再注册通用增删改查
 */
public class BaseRouteConfig {
  public static final String PREFIX = "/api/base";

  public void config(HttpRequestRouter r) {
    new BaseExtRouteConfig().config(r);
    CrudRouteRegistrar registrar = Aop.get(CrudRouteRegistrar.class);
    for (CrudDef def : BaseCrudRegistry.defs()) {
      registrar.register(r, PREFIX, def);
    }
  }
}
