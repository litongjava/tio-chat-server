package nexus.io.tio.chat.base;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.boot.ChatModule;
import nexus.io.tio.chat.config.route.BaseRouteConfig;
import nexus.io.tio.chat.facade.BaseFacade;
import nexus.io.tio.chat.facade.Facades;
import nexus.io.tio.chat.handler.InternalFacadeHandler;
import nexus.io.tio.chat.service.base.BaseFacadeLocal;
import nexus.io.tio.chat.service.base.msg.MsgSendService;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * 后台管理：租户、用户、组织、角色资源、字典参数与日志（luohuo-base-server）
 */
public class BaseModule implements ChatModule {
  public static final String SERVICE_NAME = "luohuo-base-server";
  public static final int DEFAULT_PORT = 18764;

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void config(HttpRequestRouter router) throws Exception {
    BaseFacadeLocal local = new BaseFacadeLocal();
    Facades.setBase(local);
    router.post(Facades.BASE_PATH, new InternalFacadeHandler(BaseFacade.class, local)::handle);
    new BaseRouteConfig().config(router);
    Aop.get(MsgSendService.class).startScheduler();
  }
}
