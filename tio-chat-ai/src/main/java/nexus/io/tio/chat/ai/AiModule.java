package nexus.io.tio.chat.ai;

import nexus.io.tio.chat.boot.ChatModule;
import nexus.io.tio.chat.config.route.AiRouteConfig;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * AI 模型、对话、写作、思维导图、知识库与绘图（luohuo-ai-server）
 */
public class AiModule implements ChatModule {
  public static final String SERVICE_NAME = "luohuo-ai-server";
  public static final int DEFAULT_PORT = 18877;

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void config(HttpRequestRouter router) throws Exception {
    new AiRouteConfig().config(router);
  }
}
