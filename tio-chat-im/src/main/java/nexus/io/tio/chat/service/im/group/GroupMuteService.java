package nexus.io.tio.chat.service.im.group;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.redis.Redis;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.facade.Facades;
import nexus.io.tio.chat.model.im.chat.ChatMessageReq;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.service.im.UserSummaryService;
import nexus.io.tio.chat.service.im.chat.MessageService;
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
    notifyRoom(operator, roomId, uid, "已被禁言 " + minutes + " 分钟");
    return true;
  }

  public boolean unmute(long operator, Long roomId, Long uid) {
    requireOperable(operator, roomId, uid);
    Long removed = Redis.call(jedis -> jedis.del(key(roomId, uid)));
    if (removed != null && removed > 0) {
      notifyRoom(operator, roomId, uid, "已解除禁言");
    }
    return true;
  }

  public boolean isMuted(long roomId, long uid) {
    return Boolean.TRUE.equals(Redis.call(jedis -> jedis.exists(key(roomId, uid))));
  }

  /** 群里的系统消息，让所有成员知道禁言状态的变化 */
  private void notifyRoom(long operator, long roomId, long uid, String action) {
    UserSummary user = Aop.get(UserSummaryService.class).get(uid);
    String text = (user == null ? String.valueOf(uid) : user.getName()) + action;
    ChatMessageReq req = ChatMessageReq.of(roomId, MsgType.SYSTEM, Kv.by("content", text));
    req.setSkip(true);
    Aop.get(MessageService.class).send(req, operator);
  }

  /** @return uids 中正在被禁言的成员 */
  public Set<Long> mutedOf(long roomId, List<Long> uids) {
    Set<Long> muted = new HashSet<>();
    if (uids.isEmpty()) {
      return muted;
    }
    String[] keys = uids.stream().map(uid -> key(roomId, uid)).toArray(String[]::new);
    List<String> values = Redis.call(jedis -> jedis.mget(keys));
    for (int i = 0; i < uids.size(); i++) {
      if (values.get(i) != null) {
        muted.add(uids.get(i));
      }
    }
    return muted;
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
