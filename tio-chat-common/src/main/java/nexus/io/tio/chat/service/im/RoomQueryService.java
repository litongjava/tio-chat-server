package nexus.io.tio.chat.service.im;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.consts.ImConst;

/**
 * 房间、群、单聊的基础查询
 */
public class RoomQueryService {

  public Kv getRoom(long roomId) {
    return Db.findFirstMap("select * from im_room where id=? and is_del=0", roomId);
  }

  public Kv getRoomGroupByRoomId(long roomId) {
    return Db.findFirstMap("select * from im_room_group where room_id=? and is_del=0", roomId);
  }

  public Kv getRoomFriendByRoomId(long roomId) {
    return Db.findFirstMap("select * from im_room_friend where room_id=?", roomId);
  }

  public boolean isGroup(Kv room) {
    return room != null && Objects.equals(room.getInt("type"), ImConst.ROOM_TYPE_GROUP);
  }

  public Kv getMember(long roomId, long uid) {
    String sql = """
        select m.* from im_group_member m join im_room_group g on g.id=m.group_id
        where g.room_id=? and m.uid=? and m.is_del=0 limit 1
        """;
    return Db.findFirstMap(sql, roomId, uid);
  }

  /**
   * 群成员 uid，excludeDeFriend 为 true 时排除屏蔽群聊的成员
   */
  public List<Long> groupMemberUids(long roomId, boolean excludeDeFriend) {
    String sql = """
        select m.uid from im_group_member m join im_room_group g on g.id=m.group_id
        where g.room_id=? and m.is_del=0
        """ + (excludeDeFriend ? " and m.de_friend=0" : "");
    return Db.query(sql, roomId);
  }

  /**
   * 房间内应当收到消息的成员
   */
  public List<Long> receiverUids(long roomId) {
    Kv room = getRoom(roomId);
    if (room == null) {
      return new ArrayList<>();
    }
    if (isGroup(room)) {
      return groupMemberUids(roomId, true);
    }
    Kv friend = getRoomFriendByRoomId(roomId);
    List<Long> uids = new ArrayList<>();
    if (friend != null) {
      uids.add(friend.getLong("uid1"));
      uids.add(friend.getLong("uid2"));
    }
    return uids;
  }

  public boolean isMember(long roomId, long uid) {
    Kv room = getRoom(roomId);
    if (room == null) {
      return false;
    }
    if (isGroup(room)) {
      return getMember(roomId, uid) != null;
    }
    Kv friend = getRoomFriendByRoomId(roomId);
    return friend != null && (Objects.equals(friend.getLong("uid1"), uid) || Objects.equals(friend.getLong("uid2"), uid));
  }
}
