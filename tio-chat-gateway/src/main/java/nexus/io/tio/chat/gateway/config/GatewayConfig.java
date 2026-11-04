package nexus.io.tio.chat.gateway.config;

import nexus.io.context.BootConfiguration;
import nexus.io.hook.HookCan;
import nexus.io.tio.boot.cloud.gateway.TioBootGateway;
import nexus.io.tio.boot.cloud.nacos.TioBootNacos;
import nexus.io.tio.boot.server.TioBootServer;
import nexus.io.tio.chat.gateway.filter.TokenContextFilter;
import nexus.io.tio.chat.gateway.handler.DocPageHandler;
import nexus.io.tio.chat.gateway.handler.FallbackHandler;
import nexus.io.tio.chat.gateway.handler.GateDocHandler;
import nexus.io.tio.chat.gateway.handler.OnlineServiceListHandler;
import nexus.io.tio.chat.gateway.handler.OnlineServicePrefixHandler;
import nexus.io.tio.chat.gateway.service.SessionService;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class GatewayConfig implements BootConfiguration {

  @Override
  public void config() throws Exception {
    TioBootNacos.start();
    SessionService sessionService = SessionService.fromConfig();
    HookCan.me().addDestroyMethod(sessionService::close);
    TioBootGateway.start();
    TioBootGateway.addGlobalFilter(new TokenContextFilter(sessionService));

    HttpRequestRouter router = TioBootServer.me().getRequestRouter();
    router.get("/api/gateway/findOnlineServicePrefix", new OnlineServicePrefixHandler(sessionService)::handle);
    router.get("/api/gateway/findOnlineService", new OnlineServiceListHandler(sessionService)::handle);
    router.any("/api/fallback", new FallbackHandler()::handle);
    router.get("/api/gate/doc.html", new GateDocHandler()::handle);
    router.get("/api/doc.html", new DocPageHandler()::handle);
  }
}
