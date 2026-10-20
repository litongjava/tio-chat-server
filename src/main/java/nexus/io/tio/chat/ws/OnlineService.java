package nexus.io.tio.chat.ws;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.redis.Redis;
import nexus.io.tio.chat.consts.RedisKeys;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.model.ws.WsOnlineNotify;

/**
 * 在线状态：设备级 zset、用户级 zset 以及群在线成员 set
 */
public class OnlineService {
  public static final String GROUP_ONLINE = "luohuo:group_members_online:";

  public void online(long uid, String clientId) {
    sync(uid, clientId, true);
  }

  public void offline(long uid, String clientId) {
    sync(uid, clientId, false);
  }

  private void sync(long uid, String clientId, boolean online) {
    String deviceKey = uid + ":" + clientId;
    String lockId = Redis.use().lock("syncOnline:" + uid, 10, 5);
    try {
      boolean noOtherDevices = noOtherDevices(uid, deviceKey);
      long now = System.currentTimeMillis();
      List<Long> roomIds = groupRoomIds(uid);
      if (online) {
        Redis.call(jedis -> jedis.zadd(RedisKeys.GLOBAL_DEVICES_ONLINE, now, deviceKey));
        if (noOtherDevices) {
          Redis.call(jedis -> jedis.zadd(RedisKeys.GLOBAL_USERS_ONLINE, now, String.valueOf(uid)));
          for (Long roomId : roomIds) {
            Redis.call(jedis -> jedis.sadd(GROUP_ONLINE + roomId, String.valueOf(uid)));
          }
          Db.update("update im_user set last_opt_time=now(3) where id=?", uid);
          pushStatusChange(roomIds, uid, clientId, WsRespType.ONLINE);
        }
      } else {
        Redis.call(jedis -> jedis.zrem(RedisKeys.GLOBAL_DEVICES_ONLINE, deviceKey));
        if (noOtherDevices) {
          Redis.call(jedis -> jedis.zrem(RedisKeys.GLOBAL_USERS_ONLINE, String.valueOf(uid)));
          for (Long roomId : roomIds) {
            Redis.call(jedis -> jedis.srem(GROUP_ONLINE + roomId, String.valueOf(uid)));
          }
          Db.update("update im_user set last_opt_time=now(3) where id=?", uid);
          pushStatusChange(roomIds, uid, clientId, WsRespType.OFFLINE);
        }
      }
    } finally {
      if (lockId != null) {
        Redis.use().unlock("syncOnline:" + uid, lockId);
      }
    }
  }

  private boolean noOtherDevices(long uid, String deviceKey) {
    String prefix = uid + ":";
    List<String> devices = Redis.call(jedis -> jedis.zrange(RedisKeys.GLOBAL_DEVICES_ONLINE, 0, -1));
    for (String device : devices) {
      if (device.startsWith(prefix) && !device.equals(deviceKey)) {
        return false;
      }
    }
    return true;
  }

  public boolean isOnline(long uid) {
    Double score = Redis.call(jedis -> jedis.zscore(RedisKeys.GLOBAL_USERS_ONLINE, String.valueOf(uid)));
    return score != null;
  }

  public Set<Long> onlineOf(Collection<Long> uids) {
    Set<Long> result = new HashSet<>();
    if (uids == null || uids.isEmpty()) {
      return result;
    }
    List<String> members = new ArrayList<>();
    for (Long uid : uids) {
      members.add(String.valueOf(uid));
    }
    List<Double> scores = Redis.call(jedis -> jedis.zmscore(RedisKeys.GLOBAL_USERS_ONLINE, members.toArray(new String[0])));
    for (int i = 0; i < members.size(); i++) {
      if (scores.get(i) != null) {
        result.add(Long.parseLong(members.get(i)));
      }
    }
    return result;
  }

  public long onlineCount() {
    return Redis.call(jedis -> jedis.zcard(RedisKeys.GLOBAL_USERS_ONLINE));
  }

  public Set<Long> groupOnlineMembers(long roomId) {
    Set<Long> result = new HashSet<>();
    for (String uid : Redis.call(jedis -> jedis.smembers(GROUP_ONLINE + roomId))) {
      result.add(Long.parseLong(uid));
    }
    return result;
  }

  public long groupOnlineCount(long roomId) {
    return Redis.call(jedis -> jedis.scard(GROUP_ONLINE + roomId));
  }

  /**
   * 用户加入或退出群时同步群在线成员
   */
  public void joinGroup(long roomId, long uid) {
    if (isOnline(uid)) {
      Redis.call(jedis -> jedis.sadd(GROUP_ONLINE + roomId, String.valueOf(uid)));
    }
  }

  public void leaveGroup(long roomId, long uid) {
    Redis.call(jedis -> jedis.srem(GROUP_ONLINE + roomId, String.valueOf(uid)));
  }

  private List<Long> groupRoomIds(long uid) {
    String sql = """
        select g.room_id from im_group_member m
        join im_room_group g on g.id=m.group_id and g.is_del=0
        where m.uid=? and m.is_del=0
        """;
    return Db.query(sql, uid);
  }

  private void pushStatusChange(List<Long> roomIds, long uid, String clientId, String type) {
    PushService pushService = Aop.get(PushService.class);
    long now = System.currentTimeMillis();
    List<Long> reverseFriends = Db.query("select uid from im_user_friend where friend_uid=? and is_del=0", uid);
    for (Long friendUid : reverseFriends) {
      List<Long> hisFriends = Db.query("select friend_uid from im_user_friend where uid=? and is_del=0", friendUid);
      long onlineCount = onlineOf(hisFriends).size();
      WsOnlineNotify notify = new WsOnlineNotify(String.valueOf(uid), clientId, null, now, onlineCount, 2);
      pushService.push(WsBaseResp.of(type, notify), friendUid);
    }
    for (Long roomId : roomIds) {
      Set<Long> members = groupOnlineMembers(roomId);
      if (members.isEmpty()) {
        continue;
      }
      WsOnlineNotify notify = new WsOnlineNotify(String.valueOf(uid), clientId, String.valueOf(roomId), now,
          (long) members.size(), 1);
      pushService.push(WsBaseResp.of(type, notify), members);
    }
  }
}
