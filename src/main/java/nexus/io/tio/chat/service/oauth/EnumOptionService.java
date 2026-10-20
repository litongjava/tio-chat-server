package nexus.io.tio.chat.service.oauth;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nexus.io.tio.utils.json.JsonUtils;

/**
 * 枚举下拉选项，数据来自 classpath 下的 enums.json
 */
public class EnumOptionService {
  private volatile Map<String, Object> options;

  @SuppressWarnings("unchecked")
  private Map<String, Object> options() {
    if (options == null) {
      try (InputStream in = EnumOptionService.class.getClassLoader().getResourceAsStream("enums.json")) {
        String json = in == null ? "{}" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        options = JsonUtils.parseToMap(json, String.class, Object.class);
      } catch (Exception e) {
        options = new LinkedHashMap<>();
      }
    }
    return options;
  }

  @SuppressWarnings("unchecked")
  public List<Object> find(String type) {
    Object list = options().get(type);
    return list instanceof List ? new ArrayList<>((List<Object>) list) : new ArrayList<>();
  }

  public Map<String, List<Object>> findMap(List<String> types) {
    Map<String, List<Object>> result = new LinkedHashMap<>();
    for (String type : types) {
      result.put(type, find(type));
    }
    return result;
  }
}
