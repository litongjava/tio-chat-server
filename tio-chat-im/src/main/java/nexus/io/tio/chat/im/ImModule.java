package nexus.io.tio.chat.im;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.boot.ChatModule;
import nexus.io.tio.chat.config.route.ImChatRouteConfig;
import nexus.io.tio.chat.config.route.ImFeedRouteConfig;
import nexus.io.tio.chat.config.route.ImFriendRouteConfig;
import nexus.io.tio.chat.config.route.ImMiscRouteConfig;
import nexus.io.tio.chat.config.route.ImRoomRouteConfig;
import nexus.io.tio.chat.config.route.ImUserRouteConfig;
import nexus.io.tio.chat.facade.Facades;
import nexus.io.tio.chat.facade.ImFacade;
import nexus.io.tio.chat.handler.InternalFacadeHandler;
import nexus.io.tio.chat.service.common.DelayedTaskService;
import nexus.io.tio.chat.service.im.ImFacadeLocal;
import nexus.io.tio.chat.service.im.chat.MessageService;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * 好友、会话、消息、群聊、朋友圈与表情（luohuo-im-server）
 */
public class ImModule implements ChatModule {
  public static final String SERVICE_NAME = "luohuo-im-server";
  public static final int DEFAULT_PORT = 18778;

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void config(HttpRequestRouter router) throws Exception {
    Aop.get(DelayedTaskService.class).register(MessageService.TASK_PUSH_RETRY, Aop.get(MessageService.class)::retryIfUnacked);
    ImFacadeLocal local = new ImFacadeLocal();
    Facades.setIm(local);
    router.post(Facades.IM_PATH, new InternalFacadeHandler(ImFacade.class, local)::handle);
    new ImUserRouteConfig().config(router);
    new ImChatRouteConfig().config(router);
    new ImFriendRouteConfig().config(router);
    new ImRoomRouteConfig().config(router);
    new ImMiscRouteConfig().config(router);
    new ImFeedRouteConfig().config(router);
  }
}
