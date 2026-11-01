package nexus.io.tio.chat.boot;

import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * 一个业务模块（对应原系统的一个微服务）。模块可以单独作为一个服务运行，也可以和其他模块
 * 一起打包成一个服务运行。
 */
public interface ChatModule {

  /**
   * @return 注册到 Nacos 的服务名，与网关路由一致，如 luohuo-im-server
   */
  String serviceName();

  /**
   * 服务启动前调用，用于设置连接监听器等必须在服务启动前完成的配置
   */
  default void beforeStart() {
  }

  /**
   * 注册路由、登记本模块对其他服务开放的本地实现
   */
  void config(HttpRequestRouter router) throws Exception;
}
