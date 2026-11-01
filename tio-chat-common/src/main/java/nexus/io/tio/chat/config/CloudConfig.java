package nexus.io.tio.chat.config;

import java.util.List;

import nexus.io.tio.boot.cloud.nacos.NacosProperties;
import nexus.io.tio.boot.cloud.nacos.TioBootNacos;
import nexus.io.tio.utils.environment.EnvUtils;

/**
 * 启用 nacos.discovery.enabled 时，以本进程包含的模块对应的服务名注册到 Nacos：
 * 单个服务注册一个名字，所有模块一个进程时注册全部名字，网关路由不需要改动。
 */
public class CloudConfig {

  public void config(List<String> serviceNames) {
    if (!serviceNames.isEmpty() && EnvUtils.get(NacosProperties.DISCOVERY_SERVICE) == null) {
      EnvUtils.set(NacosProperties.DISCOVERY_SERVICE, serviceNames.get(0));
      EnvUtils.set(NacosProperties.DISCOVERY_EXTRA_SERVICES, String.join(",", serviceNames.subList(1, serviceNames.size())));
    }
    TioBootNacos.start();
  }
}
