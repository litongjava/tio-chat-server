package nexus.io.tio.chat.service.system;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.common.SysConfigService;
import nexus.io.tio.chat.utils.HttpClients;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * 腾讯地图代理：坐标转换、逆地址解析与静态图
 */
public class MapService {
  private static final String BASE = "https://apis.map.qq.com/ws";

  private String key() {
    return Aop.get(SysConfigService.class).get("tencentMapKey", "");
  }

  @SuppressWarnings("unchecked")
  public Kv translate(double lat, double lng) {
    String url = BASE + "/coord/v1/translate?locations=" + lat + "," + lng + "&type=1&key=" + key();
    Map<String, Object> json = JsonUtils.parseToMap(HttpClients.get(url), String.class, Object.class);
    if (Values.toInt(json.get("status")) == 0 && json.get("locations") instanceof List) {
      List<Object> locations = (List<Object>) json.get("locations");
      Map<String, Object> loc = locations.isEmpty() ? Map.of() : (Map<String, Object>) locations.get(0);
      return Kv.by("lat", loc.get("lat")).set("lng", loc.get("lng"));
    }
    return Kv.by("status", json.get("status")).set("message", json.get("message"));
  }

  public Object reverse(double lat, double lng) {
    String url = BASE + "/geocoder/v1/?location=" + lat + "," + lng + "&key=" + key() + "&get_poi=1";
    Map<String, Object> json = JsonUtils.parseToMap(HttpClients.get(url), String.class, Object.class);
    if (Values.toInt(json.get("status")) == 0) {
      return json.get("result");
    }
    return Kv.by("status", json.get("status")).set("message", json.get("message"));
  }

  public Kv staticMap(double lat, double lng, int zoom, int width, int height) {
    String url = BASE + "/staticmap/v2/?center=" + lat + "," + lng + "&zoom=" + zoom + "&size=" + width + "*" + height
        + "&format=png&key=" + key() + "&markers=size:large|color:blue|label:A|" + lat + "," + lng;
    byte[] bytes = HttpClients.getBytes(url.replace("|", "%7C").replace("*", "%2A"));
    String base64 = bytes == null ? "" : Base64.getEncoder().encodeToString(bytes);
    return Kv.by("dataUrl", base64.isEmpty() ? "" : "data:image/png;base64," + base64);
  }
}
