package ${basePackage}.config;

import nexus.io.context.BootConfiguration;
<#if cloud>
import nexus.io.tio.boot.cloud.nacos.TioBootNacos;
</#if>
import nexus.io.tio.boot.server.TioBootServer;
import ${basePackage}.handler.HealthHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class ${appName}Config implements BootConfiguration {

  @Override
  public void config() throws Exception {
<#if cloud>
    TioBootNacos.start();

</#if>
    HttpRequestRouter router = TioBootServer.me().getRequestRouter();
    router.get("/health", new HealthHandler()::handle);
  }
}
