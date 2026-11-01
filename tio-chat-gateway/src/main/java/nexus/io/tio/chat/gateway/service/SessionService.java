package nexus.io.tio.chat.gateway.service;

import java.net.URI;

import nexus.io.tio.chat.gateway.model.GatewaySession;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.json.JsonUtils;
import redis.clients.jedis.JedisPooled;

/**
 * 按 token 读取 oauth 服务写入 Redis 的登录会话
 */
public class SessionService {
  public static final String TOKEN_SESSION = "Token:login:token:";

  private final JedisPooled jedis;

  public SessionService(JedisPooled jedis) {
    this.jedis = jedis;
  }

  public static SessionService fromConfig() {
    String host = EnvUtils.get("redis.host", "127.0.0.1");
    int port = EnvUtils.getInt("redis.port", 6379);
    String password = EnvUtils.get("redis.password");
    int database = EnvUtils.getInt("redis.database", 0);
    String uri = "redis://" + (password == null || password.isEmpty() ? "" : ":" + password + "@") + host + ":" + port
        + "/" + database;
    return new SessionService(new JedisPooled(URI.create(uri)));
  }

  public GatewaySession find(String token) {
    if (token == null || token.isEmpty()) {
      return null;
    }
    String json = jedis.get(TOKEN_SESSION + token);
    return json == null ? null : JsonUtils.parse(json, GatewaySession.class);
  }

  public void close() {
    jedis.close();
  }
}
