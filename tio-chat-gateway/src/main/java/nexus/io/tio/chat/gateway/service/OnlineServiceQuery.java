package nexus.io.tio.chat.gateway.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.tio.boot.cloud.CloudContext;
import nexus.io.tio.boot.cloud.gateway.TioBootGateway;
import nexus.io.tio.boot.cloud.gateway.route.Route;
import nexus.io.tio.utils.environment.EnvUtils;

/**
 * 查询在线服务：注册中心中有实例、并且配置了路由的服务及其路径前缀
 */
public class OnlineServiceQuery {

  public Map<String, String> prefixes() {
    Map<String, String> result = new LinkedHashMap<>();
    for (Route route : onlineRoutes()) {
      result.put(route.getServiceId(), prefix(route));
    }
    result.put(EnvUtils.get("app.name"), "gateway");
    return result;
  }

  public List<Kv> options() {
    List<Kv> result = new ArrayList<>();
    for (Route route : onlineRoutes()) {
      result.add(Kv.by("value", prefix(route)).set("text", route.getServiceId()).set("label", route.getServiceId()));
    }
    return result;
  }

  private List<Route> onlineRoutes() {
    List<Route> result = new ArrayList<>();
    for (Route route : TioBootGateway.getRouteLocator().getRoutes()) {
      if (route.isLoadBalanced() && route.getServiceId() != null
          && !CloudContext.getInstances(route.getServiceId()).isEmpty()) {
        result.add(route);
      }
    }
    return result;
  }

  /** /api/im/** -> im */
  private String prefix(Route route) {
    for (String predicate : route.getDefinition().getPredicates()) {
      if (predicate.startsWith("Path=")) {
        String first = predicate.substring("Path=".length()).split(",")[0].trim();
        String path = first.replace("/**", "");
        return path.substring(path.lastIndexOf('/') + 1);
      }
    }
    return route.getId();
  }
}
