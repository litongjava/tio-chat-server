package nexus.io.tio.chat.gateway;

import nexus.io.tio.boot.TioApplication;
import nexus.io.tio.chat.gateway.config.GatewayConfig;

/**
 * 网关，默认端口 18760，客户端只需连接这个地址
 */
public class GatewayApp {
  public static void main(String[] args) {
    TioApplication.run(GatewayApp.class, new GatewayConfig(), args);
  }
}
