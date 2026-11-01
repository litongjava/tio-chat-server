package nexus.io.tio.chat.boot;

import java.util.ArrayList;
import java.util.List;

import nexus.io.context.BootConfiguration;
import nexus.io.tio.boot.server.TioBootServer;
import nexus.io.tio.chat.config.ChatExceptionHandler;
import nexus.io.tio.chat.config.CloudConfig;
import nexus.io.tio.chat.config.DbConfig;
import nexus.io.tio.chat.config.InterceptorConfig;
import nexus.io.tio.chat.config.JsonConfig;
import nexus.io.tio.chat.config.RedisConfig;
import nexus.io.tio.chat.facade.Facades;
import nexus.io.tio.chat.handler.HealthHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * 公共启动配置：JSON、数据库、Redis、异常处理、登录拦截、健康检查和 Nacos 注册，
 * 然后依次配置本进程包含的模块。
 */
public class ChatBootConfig implements BootConfiguration {
  private final List<ChatModule> modules;

  public ChatBootConfig(List<ChatModule> modules) {
    this.modules = modules;
  }

  @Override
  public void config() throws Exception {
    List<String> serviceNames = new ArrayList<>();
    for (ChatModule module : modules) {
      serviceNames.add(module.serviceName());
    }
    new CloudConfig().config(serviceNames);
    new JsonConfig().config();
    new DbConfig().config();
    new RedisConfig().config();
    TioBootServer.me().setExceptionHandler(new ChatExceptionHandler());
    new InterceptorConfig().config();

    HttpRequestRouter router = TioBootServer.me().getRequestRouter();
    router.get("/health", new HealthHandler()::handle);
    for (ChatModule module : modules) {
      module.config(router);
    }
    // 本进程没有的模块，通过服务名远程调用
    Facades.useRemoteForMissing();
  }
}
