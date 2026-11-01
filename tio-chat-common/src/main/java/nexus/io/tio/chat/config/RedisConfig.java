package nexus.io.tio.chat.config;

import nexus.io.hook.HookCan;
import nexus.io.redis.RedisPlugin;
import nexus.io.tio.utils.environment.EnvUtils;

public class RedisConfig {

  public void config() {
    String host = EnvUtils.getStr("redis.host");
    if (host == null) {
      return;
    }
    int port = EnvUtils.getInt("redis.port", 6379);
    String password = EnvUtils.getStr("redis.password");
    int timeout = EnvUtils.getInt("redis.timeout", 60);
    int database = EnvUtils.getInt("redis.database", 0);
    String cacheName = EnvUtils.get("redis.cacheName", "main");
    RedisPlugin redisPlugin = new RedisPlugin(cacheName, host, port, timeout, password, database);
    redisPlugin.start();
    HookCan.me().addDestroyMethod(redisPlugin::stop);
  }
}
