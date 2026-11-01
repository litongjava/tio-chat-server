package nexus.io.tio.chat.boot;

import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import com.jfinal.kit.Kv;

import nexus.io.tio.boot.TioApplication;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.environment.Prop;
import nexus.io.tio.utils.environment.PropUtils;

/**
 * 启动一个包含若干模块的服务。
 *
 * <pre>
 * ChatApplication.runService(ImServerApp.class, args, "luohuo-im-server", 18778, new ImModule()); // 单个服务
 * ChatApplication.runAll(ChatServerApp.class, args, new OauthModule(), new ImModule(), ...);       // 所有模块一个服务
 * </pre>
 *
 * 公共配置在 app.properties（及 app-{env}.properties）中，每个服务的 app.name、server.port
 * 和是否注册 Nacos 作为默认值传入，配置文件、命令行参数和环境变量都可以覆盖。
 * 设置 nacos.config.data-ids 后，Nacos 中的配置在启动时自动加载，数据库、Redis 等公共配置
 * 也可以放在 Nacos 中。
 */
public class ChatApplication {

  /**
   * 单独运行一个服务：默认注册到 Nacos，其他模块通过服务名远程调用
   */
  public static void runService(Class<?> mainClass, String[] args, String serviceName, int port,
      ChatModule... modules) {
    Kv defaults = Kv.by("app.name", serviceName).set("server.port", String.valueOf(port))
        .set("nacos.discovery.enabled", "true");
    run(mainClass, args, defaults, modules);
  }

  /**
   * 所有模块运行在一个服务中：默认端口 18760，不注册 Nacos
   */
  public static void runAll(Class<?> mainClass, String[] args, ChatModule... modules) {
    Kv defaults = Kv.by("app.name", "tio-chat-server").set("server.port", "18760").set("nacos.discovery.enabled",
        "false");
    run(mainClass, args, defaults, modules);
  }

  public static void run(Class<?> mainClass, String[] args, Kv defaults, ChatModule... modules) {
    long start = System.currentTimeMillis();
    // 与数据库保持同一时区，保证 Java 与 SQL 生成的时间可以直接比较
    String timeZone = System.getProperty("app.time-zone", System.getenv().getOrDefault("APP_TIME_ZONE", "Asia/Shanghai"));
    TimeZone.setDefault(TimeZone.getTimeZone(timeZone));

    EnvUtils.load(args);
    applyDefaults(defaults);
    List<ChatModule> list = Arrays.asList(modules);
    for (ChatModule module : list) {
      module.beforeStart();
    }
    TioApplication.run(mainClass, new ChatBootConfig(list), args);
    System.out.println((System.currentTimeMillis() - start) + "ms");
  }

  /**
   * 仅在配置文件、命令行和环境变量都没有设置时使用默认值
   */
  private static void applyDefaults(Kv defaults) {
    Prop prop = new Prop();
    for (Object key : defaults.keySet()) {
      String name = key.toString();
      if (EnvUtils.get(name) == null) {
        prop.getProperties().setProperty(name, defaults.getStr(name));
      }
    }
    PropUtils.append(prop);
  }
}
