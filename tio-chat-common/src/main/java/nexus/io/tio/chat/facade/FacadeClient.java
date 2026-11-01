package nexus.io.tio.chat.facade;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.jfinal.kit.Kv;

import nexus.io.tio.boot.cloud.client.ServiceClient;
import nexus.io.tio.boot.cloud.client.ServiceRequest;
import nexus.io.tio.boot.cloud.client.ServiceResponse;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.utils.environment.EnvUtils;

/**
 * 调用方：按服务名远程调用其他服务开放的能力。请求发往对方的内部接口
 * /internal/facade/{name}，不经过网关。
 */
public class FacadeClient {
  public static final String SECRET_HEADER = "x-internal-secret";

  @SuppressWarnings("unchecked")
  public static <T> T create(Class<T> facadeType, String serviceId, String path) {
    return (T) Proxy.newProxyInstance(facadeType.getClassLoader(), new Class<?>[] { facadeType },
        (proxy, method, args) -> {
          if (method.getDeclaringClass() == Object.class) {
            return method.invoke(new Object(), args);
          }
          return call(serviceId, path, method, args);
        });
  }

  private static Object call(String serviceId, String path, Method method, Object[] args) {
    List<Object> list = args == null ? new ArrayList<>() : Arrays.asList(args);
    String body = JSON.toJSONString(Kv.by("method", method.getName()).set("args", list),
        JSONWriter.Feature.WriteNulls);
    ServiceResponse response = ServiceClient.me().call(serviceId,
        ServiceRequest.post(path).header(SECRET_HEADER, secret()).json(body));
    JSONObject result = JSON.parseObject(response.getBodyString());
    if (result == null || !result.getBooleanValue("success")) {
      String msg = result == null ? "HTTP " + response.getStatus() : result.getString("msg");
      Integer code = result == null ? null : result.getInteger("code");
      // 对方的业务错误原样抛出，客户端看到的错误码和提示与同进程调用一致
      throw ChatErrors.of(code == null ? ChatCode.SERVER_ERROR : code, msg);
    }
    if (method.getReturnType() == void.class) {
      return null;
    }
    Object data = result.get("data");
    if (data == null) {
      return method.getReturnType().isPrimitive() ? defaultPrimitive(method.getReturnType()) : null;
    }
    return JSON.parseObject(JSON.toJSONString(data), method.getGenericReturnType());
  }

  private static Object defaultPrimitive(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    return 0L;
  }

  public static String secret() {
    return EnvUtils.get("chat.internal.secret", "tio-chat-internal");
  }
}
