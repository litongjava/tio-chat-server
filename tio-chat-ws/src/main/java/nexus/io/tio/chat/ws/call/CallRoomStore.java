package nexus.io.tio.chat.ws.call;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nexus.io.redis.Redis;

/**
 * 通话房间状态，保存在 Redis 中，多节点共享
 */
public class CallRoomStore {
  private static final String META = "luohuo:video:meta:";
  private static final String MEMBERS = "luohuo:video:members:";
  private static final String USER_ROOMS = "luohuo:video:userRooms:";
  private static final int TTL = 24 * 3600;

  public void open(long roomId, long creator, boolean video, int roomType) {
    String key = META + roomId;
    Redis.call(jedis -> {
      jedis.del(key);
      jedis.hset(key, Map.of("closed", "0", "creator", String.valueOf(creator), "video", video ? "1" : "0", "type",
          String.valueOf(roomType), "admins", String.valueOf(creator)));
      jedis.expire(key, TTL);
      return null;
    });
  }

  public boolean isClosed(long roomId) {
    String closed = Redis.call(jedis -> jedis.hget(META + roomId, "closed"));
    return closed == null || "1".equals(closed);
  }

  public void markClosed(long roomId) {
    Redis.call(jedis -> jedis.hset(META + roomId, "closed", "1"));
  }

  public Long creator(long roomId) {
    String value = Redis.call(jedis -> jedis.hget(META + roomId, "creator"));
    return value == null ? null : Long.parseLong(value);
  }

  public boolean isVideo(long roomId) {
    return "1".equals(Redis.call(jedis -> jedis.hget(META + roomId, "video")));
  }

  public Integer roomType(long roomId) {
    String value = Redis.call(jedis -> jedis.hget(META + roomId, "type"));
    return value == null ? null : Integer.parseInt(value);
  }

  public void setStartTime(long roomId) {
    Redis.call(jedis -> jedis.hset(META + roomId, "startTime", String.valueOf(System.currentTimeMillis())));
  }

  public Long startTime(long roomId) {
    String value = Redis.call(jedis -> jedis.hget(META + roomId, "startTime"));
    return value == null ? null : Long.parseLong(value);
  }

  public boolean isAdmin(long uid, long roomId) {
    String admins = Redis.call(jedis -> jedis.hget(META + roomId, "admins"));
    if (admins == null) {
      return false;
    }
    for (String admin : admins.split(",")) {
      if (admin.equals(String.valueOf(uid))) {
        return true;
      }
    }
    return false;
  }

  public void set(long roomId, String field, String value) {
    Redis.call(jedis -> jedis.hset(META + roomId, field, value));
  }

  public void join(long uid, long roomId) {
    Redis.call(jedis -> {
      jedis.sadd(MEMBERS + roomId, String.valueOf(uid));
      jedis.expire(MEMBERS + roomId, TTL);
      jedis.sadd(USER_ROOMS + uid, String.valueOf(roomId));
      return null;
    });
  }

  public void leave(long uid, long roomId) {
    Redis.call(jedis -> {
      jedis.srem(MEMBERS + roomId, String.valueOf(uid));
      jedis.srem(USER_ROOMS + uid, String.valueOf(roomId));
      return null;
    });
  }

  public boolean isMember(long uid, long roomId) {
    return Redis.call(jedis -> jedis.sismember(MEMBERS + roomId, String.valueOf(uid)));
  }

  public List<Long> members(long roomId) {
    List<Long> result = new ArrayList<>();
    for (String uid : Redis.call(jedis -> jedis.smembers(MEMBERS + roomId))) {
      result.add(Long.parseLong(uid));
    }
    return result;
  }

  public List<Long> userRooms(long uid) {
    List<Long> result = new ArrayList<>();
    for (String roomId : Redis.call(jedis -> jedis.smembers(USER_ROOMS + uid))) {
      result.add(Long.parseLong(roomId));
    }
    return result;
  }

  public void clearMembers(long roomId) {
    for (Long uid : members(roomId)) {
      Redis.call(jedis -> jedis.srem(USER_ROOMS + uid, String.valueOf(roomId)));
    }
    Redis.call(jedis -> jedis.del(MEMBERS + roomId));
  }
}
