package nexus.io.tio.chat.service.im;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.common.CursorPageBaseResp;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.chat.ws.OnlineService;
import nexus.io.tio.chat.ws.PushService;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

public class FriendService {

  /**
   * 建立双向好友关系并创建双方会话
   */
  public void createFriend(long roomId, long uid, long friendUid, long tenantId) {
    saveFriend(roomId, uid, friendUid, tenantId);
    saveFriend(roomId, friendUid, uid, tenantId);
    ContactService contactService = Aop.get(ContactService.class);
    contactService.createContact(uid, roomId, tenantId);
    contactService.createContact(friendUid, roomId, tenantId);
  }

  private void saveFriend(long roomId, long uid, long friendUid, long tenantId) {
    Long exists = Db.queryLong("select id from im_user_friend where uid=? and friend_uid=? and is_del=0", uid,
        friendUid);
    if (exists != null) {
      Db.update("update im_user_friend set room_id=? where id=?", roomId, exists);
      return;
    }
    Row friend = Row.by("id", SnowflakeIdUtils.id()).set("room_id", roomId).set("uid", uid)
        .set("friend_uid", friendUid).set("is_temp", 0).set("tenant_id", tenantId).set("create_by", uid);
    DbRows.save("im_user_friend", friend);
  }

  public boolean isFriend(long uid, long friendUid) {
    Long id = Db.queryLong("select id from im_user_friend where uid=? and friend_uid=? and is_del=0 limit 1", uid,
        friendUid);
    return id != null;
  }

  public Kv check(long uid, List<Long> uidList) {
    List<Kv> checked = new ArrayList<>();
    for (Long friendUid : uidList) {
      checked.add(Kv.by("uid", friendUid).set("isFriend", isFriend(uid, friendUid)));
    }
    return Kv.by("checkedList", checked);
  }

  /**
   * 按账号模糊搜索用户
   */
  public List<Kv> search(String key) {
    List<Kv> result = new ArrayList<>();
    if (key == null || key.isBlank()) {
      return result;
    }
    for (Kv u : Db.findMaps("select id, name, avatar, account, gitee_id, github_id, gitcode_id from im_user where account like ? and is_del=0",
        "%" + key.trim() + "%")) {
      result.add(Kv.by("uid", u.getLong("id")).set("name", u.getStr("name")).set("avatar", u.getStr("avatar"))
          .set("account", u.getStr("account")).set("linkedGitee", notBlank(u.getStr("gitee_id")))
          .set("linkedGithub", notBlank(u.getStr("github_id"))).set("linkedGitcode", notBlank(u.getStr("gitcode_id"))));
    }
    return result;
  }

  private boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  /**
   * 删除好友：不能删除系统机器人，删除后禁用单聊房间并通知对方
   */
  public void delete(long uid, Long friendUid) {
    ChatErrors.require(friendUid != null, "targetUid不能为空");
    List<Kv> relations = Db.findMaps(
        "select * from im_user_friend where ((uid=? and friend_uid=?) or (uid=? and friend_uid=?)) and is_del=0", uid, friendUid,
        friendUid, uid);
    if (relations.isEmpty() || friendUid == ImConst.DEF_BOT_ID) {
      return;
    }
    long roomId = relations.get(0).getLong("room_id");
    Db.tx(() -> {
      for (Kv relation : relations) {
        Db.update("update im_user_friend set is_del=1 where id=?", relation.getLong("id"));
      }
      Db.update("delete from im_contact where room_id=? and uid=?", roomId, uid);
      Db.update("update im_room_friend set is_del=1 where room_key=?", RoomService.roomKey(uid, friendUid));
      return true;
    });
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.DELETE_FRIEND, String.valueOf(uid)), friendUid);
  }

  public CursorPageBaseResp<Kv> page(long uid, String cursor, int pageSize) {
    List<Object> params = new ArrayList<>();
    StringBuilder where = new StringBuilder(" from im_user_friend where uid=? and is_del=0");
    params.add(uid);
    long total = Db.queryLong("select count(*)" + where, params.toArray());
    if (cursor != null && !cursor.isBlank() && !"null".equals(cursor)) {
      where.append(" and id<?");
      params.add(Long.parseLong(cursor));
    }
    params.add(pageSize);
    List<Kv> rows = Db.findMaps("select *" + where + " order by id desc limit ?", params.toArray());
    if (rows.isEmpty()) {
      return CursorPageBaseResp.empty();
    }
    List<Long> friendUids = new ArrayList<>();
    for (Kv row : rows) {
      friendUids.add(row.getLong("friend_uid"));
    }
    Set<Long> online = Aop.get(OnlineService.class).onlineOf(friendUids);
    Map<Long, UserSummary> users = Aop.get(UserSummaryService.class).getBatch(friendUids);
    List<Kv> list = new ArrayList<>();
    for (Kv row : rows) {
      long friendUid = row.getLong("friend_uid");
      Kv resp = Kv.by("uid", friendUid).set("remark", row.getStr("remark"))
          .set("activeStatus", online.contains(friendUid) ? 1 : 2).set("hideMyPosts", Values.toBool(row.get("hide_my_posts")))
          .set("hideTheirPosts", Values.toBool(row.get("hide_their_posts")));
      UserSummary user = users.get(friendUid);
      if (user != null) {
        resp.set("name", user.getName()).set("account", user.getAccount()).set("avatar", user.getAvatar());
      }
      list.add(resp);
    }
    String next = String.valueOf(rows.get(rows.size() - 1).getLong("id"));
    return new CursorPageBaseResp<>(next, rows.size() != pageSize, list, total);
  }

  public boolean updateRemark(long uid, Long friendUid, String remark) {
    Kv relation = Db.findFirstMap("select id from im_user_friend where uid=? and friend_uid=? and is_del=0", uid, friendUid);
    ChatErrors.require(relation != null, "你俩不是好友关系!");
    return Db.update("update im_user_friend set remark=? where id=?", remark, relation.getLong("id")) > 0;
  }

  public boolean permissionSettings(long uid, Long friendUid, Boolean hideMyPosts, Boolean hideTheirPosts) {
    Kv relation = Db.findFirstMap("select id from im_user_friend where uid=? and friend_uid=? and is_del=0", uid, friendUid);
    ChatErrors.require(relation != null, "你们不是好友!");
    return Db.update("update im_user_friend set hide_my_posts=?, hide_their_posts=? where id=?", Values.isTrue(hideMyPosts),
        Values.isTrue(hideTheirPosts), relation.getLong("id")) > 0;
  }
}
