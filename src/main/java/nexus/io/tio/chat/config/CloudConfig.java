package nexus.io.tio.chat.config;

import nexus.io.tio.boot.cloud.nacos.TioBootNacos;

/**
 * Registers the server with Nacos when nacos.discovery.enabled=true, under the
 * service names the gateway routes to (nacos.discovery.service and
 * nacos.discovery.extra-services). Disabled by default, so a single process
 * still runs without Nacos.
 */
public class CloudConfig {

  public void config() {
    TioBootNacos.start();
  }
}
