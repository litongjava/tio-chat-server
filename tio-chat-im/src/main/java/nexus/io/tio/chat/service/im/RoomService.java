package nexus.io.tio.chat.service.im;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

public class RoomService {

  public static String roomKey(long uid1, long uid2) {
    List<Long> uids = new ArrayList<>(List.of(uid1, uid2));
    Collections.sort(uids);
    return uids.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  public long createRoom(int type, long tenantId) {
    long roomId = SnowflakeIdUtils.id();
    Row room = Row.by("id", roomId).set("type", type).set("hot_flag", 0).set("tenant_id", tenantId)
        .set("create_by", 1L);
    DbRows.save("im_room", room);
    return roomId;
  }

  public Kv getFriendRoom(long uid1, long uid2) {
    return Db.findFirstMap("select * from im_room_friend where room_key=? and is_del=0", roomKey(uid1, uid2));
  }

  /**
   * 创建单聊房间，已存在时恢复
   */
  public long createFriendRoom(long uid1, long uid2, long tenantId) {
    String key = roomKey(uid1, uid2);
    Kv exists = Db.findFirstMap("select id, room_id from im_room_friend where room_key=?", key);
    if (exists != null) {
      Db.update("update im_room_friend set de_friend1=0, de_friend2=0, is_del=0 where id=?", exists.getLong("id"));
      return exists.getLong("room_id");
    }
    long roomId = createRoom(ImConst.ROOM_TYPE_FRIEND, tenantId);
    long small = Math.min(uid1, uid2);
    long big = Math.max(uid1, uid2);
    Row roomFriend = Row.by("id", SnowflakeIdUtils.id()).set("room_id", roomId).set("uid1", small).set("uid2", big)
        .set("room_key", key).set("de_friend1", 0).set("de_friend2", 0).set("tenant_id", tenantId)
        .set("create_by", 1L);
    DbRows.save("im_room_friend", roomFriend);
    return roomId;
  }

  public void createGroupMember(long groupId, long uid, int roleId, long tenantId) {
    Long exists = Db.queryLong("select id from im_group_member where group_id=? and uid=? and is_del=0", groupId, uid);
    if (exists != null) {
      return;
    }
    Row member = Row.by("id", SnowflakeIdUtils.id()).set("group_id", groupId).set("uid", uid).set("role_id", roleId)
        .set("remark", "").set("de_friend", 0).set("tenant_id", tenantId).set("create_by", uid);
    DbRows.save("im_group_member", member);
  }
}
