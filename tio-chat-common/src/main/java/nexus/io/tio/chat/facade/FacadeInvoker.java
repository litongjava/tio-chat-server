package nexus.io.tio.chat.facade;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 服务端：把 {"method": "...", "args": [...]} 调用到本地实现上
 */
public class FacadeInvoker {
  private final Class<?> facadeType;
  private final Object target;

  public FacadeInvoker(Class<?> facadeType, Object target) {
    this.facadeType = facadeType;
    this.target = target;
  }

  /**
   * @param body 调用请求
   * @return 方法返回值
   */
  public Object invoke(String body) throws Throwable {
    JSONObject request = JSON.parseObject(body);
    String name = request.getString("method");
    JSONArray args = request.getJSONArray("args");
    int count = args == null ? 0 : args.size();
    Method method = find(name, count);
    Type[] types = method.getGenericParameterTypes();
    Object[] values = new Object[count];
    for (int i = 0; i < count; i++) {
      Object arg = args.get(i);
      values[i] = arg == null ? null : JSON.parseObject(JSON.toJSONString(arg), types[i]);
    }
    try {
      return method.invoke(target, values);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private Method find(String name, int count) {
    for (Method method : facadeType.getMethods()) {
      if (method.getName().equals(name) && method.getParameterCount() == count) {
        return method;
      }
    }
    throw new IllegalArgumentException("unknown facade method " + facadeType.getSimpleName() + "." + name);
  }

  public static List<Object> args(Object... values) {
    return List.of(values);
  }
}
