package nexus.io.tio.chat.service.base.msg;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;

/**
 * 执行消息接口和消息模板中配置的 Groovy 脚本。脚本在服务端运行，只有管理员可以修改。
 * 接口脚本定义一个实现 MsgStrategy 的类；模板脚本读取模板参数，返回用于渲染模板的 Map。
 */
final class MsgGroovy {
  private static final GroovyClassLoader LOADER = new GroovyClassLoader(MsgGroovy.class.getClassLoader());
  private static final Map<String, Class<?>> CLASSES = new ConcurrentHashMap<>();

  private MsgGroovy() {
  }

  static MsgStrategy strategy(String script) throws Exception {
    Object instance = compile(script).getDeclaredConstructor().newInstance();
    if (!(instance instanceof MsgStrategy)) {
      throw new IllegalArgumentException("消息接口脚本必须实现 " + MsgStrategy.class.getName());
    }
    return (MsgStrategy) instance;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> templateValues(String script, Map<String, Object> params) {
    Class<?> type = compile(script);
    Script instance;
    try {
      instance = (Script) type.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("模板脚本无法执行", e);
    }
    instance.setBinding(new Binding(new LinkedHashMap<>(params)));
    Object result = instance.run();
    if (!(result instanceof Map)) {
      throw new IllegalArgumentException("模板脚本必须返回 Map");
    }
    return (Map<String, Object>) result;
  }

  private static Class<?> compile(String script) {
    if (script == null || script.trim().isEmpty()) {
      throw new IllegalArgumentException("脚本不能为空");
    }
    return CLASSES.computeIfAbsent(script, LOADER::parseClass);
  }
}
