package nexus.io.tio.chat.gateway.config;

import nexus.io.context.BootConfiguration;
import nexus.io.hook.HookCan;
import nexus.io.tio.boot.cloud.gateway.TioBootGateway;
import nexus.io.tio.boot.cloud.nacos.TioBootNacos;
import nexus.io.tio.chat.gateway.filter.TokenContextFilter;
import nexus.io.tio.chat.gateway.service.SessionService;

public class GatewayConfig implements BootConfiguration {

  @Override
  public void config() throws Exception {
    TioBootNacos.start();
    SessionService sessionService = SessionService.fromConfig();
    HookCan.me().addDestroyMethod(sessionService::close);
    TioBootGateway.start();
    TioBootGateway.addGlobalFilter(new TokenContextFilter(sessionService));
  }
}
