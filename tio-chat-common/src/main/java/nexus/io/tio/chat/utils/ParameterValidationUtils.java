package nexus.io.tio.chat.utils;

import com.jfinal.kit.Kv;

import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.validator.ParameterValidator;

/**
 * 参数校验工具，校验失败抛出 ParameterValidationException，由全局异常处理器统一转换
 */
public class ParameterValidationUtils {

  public static String requestBody(HttpRequest request, int maxLength) {
    String body = request.getBodyString();
    ParameterValidator.require(body == null || body.length() <= maxLength, "请求体过大");
    return body;
  }

  /**
   * 绑定 JSON 正文；客户端在 GET 请求中也会发送空正文，空正文按空对象处理
   */
  public static <T> T parseBody(HttpRequest request, Class<T> type) {
    String body = request.getBodyString();
    if (body == null || body.isBlank()) {
      return JsonUtils.parse("{}", type);
    }
    return request.getBodyObject(type);
  }

  public static Kv bodyKv(HttpRequest request) {
    String body = request.getBodyString();
    if (body == null || body.isBlank()) {
      return Kv.create();
    }
    return request.getBodyObject(Kv.class);
  }

  public static void required(Object value, String message) {
    ParameterValidator.require(value != null, message);
  }

  public static void notBlank(String value, String message) {
    ParameterValidator.require(value != null && !value.isBlank(), message);
  }

  public static void isTrue(boolean condition, String message) {
    ParameterValidator.require(condition, message);
  }

  public static Long requiredLong(HttpRequest request, String name) {
    String value = request.getParam(name);
    ParameterValidator.require(value != null && !value.isBlank(), name + "不能为空");
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      ParameterValidator.require(false, name + "格式不正确");
      return null;
    }
  }

  public static Long optionalLong(HttpRequest request, String name) {
    String value = request.getParam(name);
    if (value == null || value.isBlank() || "null".equals(value) || "undefined".equals(value)) {
      return null;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      ParameterValidator.require(false, name + "格式不正确");
      return null;
    }
  }

  public static int optionalInt(HttpRequest request, String name, int defaultValue) {
    String value = request.getParam(name);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      ParameterValidator.require(false, name + "格式不正确");
      return defaultValue;
    }
  }
}
