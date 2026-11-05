package nexus.io.tio.chat.service.im.group;

import java.util.Objects;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.redis.Redis;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.facade.Facades;
import nexus.io.tio.chat.utils.ChatErrors;

/**
 * 群成员禁言。保存在 Redis 中，到期自动解除，多个 im 实例共享
 */
public class GroupMuteService {
  private static final String KEY = "luohuo:group:mute:";
  private static final long MAX_MINUTES = 30L * 24 * 60;
  private static final long SECONDS_PER_MINUTE = 60;

  private String key(long roomId, long uid) {
    return KEY + roomId + ":" + uid;
  }

  /** @param minutes 禁言时长（分钟） */
  public boolean mute(long operator, Long roomId, Long uid, Long minutes) {
    ChatErrors.require(minutes != null && minutes > 0 && minutes <= MAX_MINUTES, "禁言时长必须在 1 到 43200 分钟之间");
    requireOperable(operator, roomId, uid);
    Redis.call(jedis -> jedis.setex(key(roomId, uid), minutes * SECONDS_PER_MINUTE, String.valueOf(operator)));
    return true;
  }

  public boolean unmute(long operator, Long roomId, Long uid) {
    requireOperable(operator, roomId, uid);
    Redis.call(jedis -> jedis.del(key(roomId, uid)));
    return true;
  }

  public boolean isMuted(long roomId, long uid) {
    return Boolean.TRUE.equals(Redis.call(jedis -> jedis.exists(key(roomId, uid))));
  }

  /** 系统管理员和群主可以操作任何成员，群管理员只能操作普通成员，群主不能被禁言 */
  private void requireOperable(long operator, Long roomId, Long uid) {
    GroupService groups = Aop.get(GroupService.class);
    Kv group = groups.requireGroup(roomId);
    long groupId = group.getLong("id");
    Integer target = groups.role(uid, groupId);
    ChatErrors.require(Objects.equals(target, ImConst.GROUP_ROLE_LEADER) || Objects.equals(target, ImConst.GROUP_ROLE_MANAGER) || Objects.equals(target, ImConst.GROUP_ROLE_MEMBER), "该用户不是群成员");
    ChatErrors.require(!Objects.equals(target, ImConst.GROUP_ROLE_LEADER), "不能禁言群主");
    if (Facades.base().isAdmin(operator)) {
      return;
    }
    Integer role = groups.role(operator, groupId);
    boolean leader = Objects.equals(role, ImConst.GROUP_ROLE_LEADER);
    boolean manager = Objects.equals(role, ImConst.GROUP_ROLE_MANAGER) && Objects.equals(target, ImConst.GROUP_ROLE_MEMBER);
    ChatErrors.require(leader || manager, "无权禁言该成员");
  }
}
