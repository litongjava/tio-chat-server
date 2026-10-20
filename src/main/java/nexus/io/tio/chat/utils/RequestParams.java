package nexus.io.tio.chat.utils;

import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.tio.http.common.HttpRequest;

/**
 * 查询参数转 Kv，多值参数取第一个
 */
public class RequestParams {

  public static Kv toKv(HttpRequest request) {
    Kv kv = Kv.create();
    Map<String, Object> params = request.getParam();
    if (params != null) {
      for (Map.Entry<String, Object> entry : params.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof Object[]) {
          Object[] values = (Object[]) value;
          value = values.length == 0 ? null : values[0];
        }
        kv.set(entry.getKey(), value);
      }
    }
    return kv;
  }
}
