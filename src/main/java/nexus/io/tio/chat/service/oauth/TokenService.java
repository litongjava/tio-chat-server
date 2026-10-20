package nexus.io.tio.chat.service.oauth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import nexus.io.redis.Redis;
import nexus.io.tio.chat.consts.RedisKeys;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * 不透明 token 会话管理，语义与 Sa-Token 一致：
 * 同一账号同一设备类型只保留一个 token，refresh token 一次性使用。
 */
public class TokenService {

  public long tokenTimeout() {
    return EnvUtils.getLong("chat.token.timeout", 2592000L);
  }

  public long refreshTimeout() {
    return tokenTimeout() * 2;
  }

  /**
   * 创建会话，返回被挤下线的旧 token
   */
  public String login(LoginSession session) {
    String token = UUID.randomUUID().toString();
    session.setToken(token);
    session.setLoginTime(System.currentTimeMillis());
    String device = deviceKey(session.getSystemType(), session.getDeviceType());
    String userKey = RedisKeys.USER_TOKENS + session.getUserId();
    long timeout = tokenTimeout();
    String json = JsonUtils.toJson(session);
    return Redis.call(jedis -> {
      String oldToken = jedis.hget(userKey, device);
      if (oldToken != null) {
        jedis.del(RedisKeys.TOKEN_SESSION + oldToken);
      }
      jedis.setex(RedisKeys.TOKEN_SESSION + token, timeout, json);
      jedis.hset(userKey, device, token);
      jedis.expire(userKey, timeout);
      return oldToken;
    });
  }

  public LoginSession getSession(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    String json = Redis.call(jedis -> jedis.get(RedisKeys.TOKEN_SESSION + token));
    if (json == null) {
      return null;
    }
    return JsonUtils.parse(json, LoginSession.class);
  }

  public boolean exists(String token) {
    return Redis.call(jedis -> jedis.exists(RedisKeys.TOKEN_SESSION + token));
  }

  public void logout(LoginSession session) {
    String device = deviceKey(session.getSystemType(), session.getDeviceType());
    String userKey = RedisKeys.USER_TOKENS + session.getUserId();
    Redis.call(jedis -> {
      jedis.del(RedisKeys.TOKEN_SESSION + session.getToken());
      String current = jedis.hget(userKey, device);
      if (session.getToken().equals(current)) {
        jedis.hdel(userKey, device);
      }
      return null;
    });
  }

  /**
   * 踢掉某个用户的全部 token
   */
  public List<String> kickoutAll(Long userId) {
    String userKey = RedisKeys.USER_TOKENS + userId;
    return Redis.call(jedis -> {
      Map<String, String> all = jedis.hgetAll(userKey);
      List<String> tokens = new ArrayList<>(all.values());
      for (String token : tokens) {
        jedis.del(RedisKeys.TOKEN_SESSION + token);
      }
      jedis.del(userKey);
      return tokens;
    });
  }

  public boolean hasAnyToken(Long userId) {
    String userKey = RedisKeys.USER_TOKENS + userId;
    return Redis.call(jedis -> {
      Map<String, String> all = jedis.hgetAll(userKey);
      for (String token : all.values()) {
        if (jedis.exists(RedisKeys.TOKEN_SESSION + token)) {
          return true;
        }
      }
      return false;
    });
  }

  public String createRefreshToken(LoginSession session) {
    String refreshToken = UUID.randomUUID().toString().replace("-", "");
    String json = JsonUtils.toJson(session);
    long timeout = refreshTimeout();
    Redis.call(jedis -> jedis.setex(RedisKeys.REFRESH_TOKEN + refreshToken, timeout, json));
    return refreshToken;
  }

  /**
   * 消费 refresh token，返回其关联的会话信息
   */
  public LoginSession consumeRefreshToken(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return null;
    }
    String key = RedisKeys.REFRESH_TOKEN + refreshToken;
    String json = Redis.call(jedis -> jedis.getDel(key));
    if (json == null) {
      return null;
    }
    return JsonUtils.parse(json, LoginSession.class);
  }

  private String deviceKey(Integer systemType, String deviceType) {
    return String.valueOf(systemType) + (deviceType == null ? "" : deviceType);
  }

  /**
   * 踢掉单个 token
   */
  public void kickoutToken(String token) {
    LoginSession session = getSession(token);
    Redis.call(jedis -> jedis.del(RedisKeys.TOKEN_SESSION + token));
    if (session != null) {
      String userKey = RedisKeys.USER_TOKENS + session.getUserId();
      Redis.call(jedis -> {
        Map<String, String> all = jedis.hgetAll(userKey);
        for (Map.Entry<String, String> entry : all.entrySet()) {
          if (token.equals(entry.getValue())) {
            jedis.hdel(userKey, entry.getKey());
          }
        }
        return null;
      });
    }
  }

  /**
   * 当前在线的账号 id
   */
  public List<Long> onlineUserIds() {
    List<Long> result = new ArrayList<>();
    for (String key : Redis.call(jedis -> jedis.keys(RedisKeys.USER_TOKENS + "*"))) {
      if (hasAnyToken(Long.parseLong(key.substring(RedisKeys.USER_TOKENS.length())))) {
        result.add(Long.parseLong(key.substring(RedisKeys.USER_TOKENS.length())));
      }
    }
    return result;
  }

  /**
   * 某账号的全部有效会话
   */
  public List<LoginSession> sessions(Long userId) {
    List<LoginSession> result = new ArrayList<>();
    Map<String, String> all = Redis.call(jedis -> jedis.hgetAll(RedisKeys.USER_TOKENS + userId));
    for (String token : all.values()) {
      LoginSession session = getSession(token);
      if (session != null) {
        result.add(session);
      }
    }
    return result;
  }

  public long ttl(String token) {
    return Redis.call(jedis -> jedis.ttl(RedisKeys.TOKEN_SESSION + token));
  }
}
