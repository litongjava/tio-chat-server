package nexus.io.tio.chat.service.im.chat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.model.common.CursorPageBaseResp;
import nexus.io.tio.chat.model.im.chat.ChatRoomResp;
import nexus.io.tio.chat.model.im.chat.MessageRecord;
import nexus.io.tio.chat.service.im.RoomQueryService;
import nexus.io.tio.chat.service.im.RoomService;
import nexus.io.tio.chat.service.im.chat.msg.MsgHandler;
import nexus.io.tio.chat.service.im.chat.msg.MsgHandlers;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;

/**
 * 会话列表组装：名称、头像、最后一条消息与未读数
 */
public class ContactQueryService {

  public List<ChatRoomResp> list(long uid) {
    List<Kv> contacts = Db.findMaps("select * from im_contact where uid=? and is_del=0", uid);
    Map<Long, Kv> contactMap = new LinkedHashMap<>();
    for (Kv contact : contacts) {
      contactMap.putIfAbsent(contact.getLong("room_id"), contact);
    }
    return build(contactMap, uid, new ArrayList<>(contactMap.keySet()));
  }

  public CursorPageBaseResp<ChatRoomResp> page(long uid, String cursor, int pageSize) {
    List<Object> params = new ArrayList<>();
    StringBuilder where = new StringBuilder(" from im_contact where uid=? and hide=0 and is_del=0");
    params.add(uid);
    long total = Db.queryLong("select count(*)" + where, params.toArray());
    if (cursor != null && !cursor.isBlank() && !"null".equals(cursor)) {
      where.append(" and active_time<?");
      params.add(LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(cursor)), ZoneId.systemDefault()));
    }
    params.add(pageSize);
    List<Kv> rows = Db.findMaps("select *" + where + " order by active_time desc limit ?", params.toArray());
    if (rows.isEmpty()) {
      return CursorPageBaseResp.empty();
    }
    Map<Long, Kv> contactMap = new LinkedHashMap<>();
    for (Kv row : rows) {
      contactMap.put(row.getLong("room_id"), row);
    }
    LocalDateTime last = Values.time(rows.get(rows.size() - 1).get("active_time"));
    String next = last == null ? null : String.valueOf(last.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    List<ChatRoomResp> list = build(contactMap, uid, new ArrayList<>(contactMap.keySet()));
    return new CursorPageBaseResp<>(next, rows.size() != pageSize, list, total);
  }

  public ChatRoomResp detail(long uid, long roomId) {
    Map<Long, Kv> contactMap = contactMap(uid);
    List<ChatRoomResp> list = build(contactMap, uid, List.of(roomId));
    ChatErrors.require(!list.isEmpty(), "房间号有误");
    return list.get(0);
  }

  /**
   * roomType=1 时 id 为群房间号，否则 id 为好友 uid
   */
  public ChatRoomResp detailByFriend(long uid, Long id, Integer roomType) {
    ChatErrors.require(id != null, "id不能为空");
    RoomQueryService roomQueryService = Aop.get(RoomQueryService.class);
    if (Objects.equals(roomType, ImConst.ROOM_TYPE_GROUP)) {
      ChatErrors.require(roomQueryService.getMember(id, uid) != null, "您不是群成员");
      return detail(uid, id);
    }
    String sql = "select room_id from im_room_friend where room_key=?";
    Long roomId = Db.queryLong(sql, RoomService.roomKey(uid, id));
    ChatErrors.require(roomId != null, "他不是您的好友");
    return detail(uid, roomId);
  }

  private Map<Long, Kv> contactMap(long uid) {
    Map<Long, Kv> contactMap = new HashMap<>();
    for (Kv contact : Db.findMaps("select * from im_contact where uid=? and is_del=0", uid)) {
      contactMap.putIfAbsent(contact.getLong("room_id"), contact);
    }
    return contactMap;
  }

  public List<ChatRoomResp> build(Map<Long, Kv> contactMap, long uid, List<Long> roomIds) {
    List<ChatRoomResp> result = new ArrayList<>();
    if (roomIds.isEmpty()) {
      return result;
    }
    String in = Values.placeholders(roomIds.size());
    List<Kv> rooms = Db.findMaps("select * from im_room where id in (" + in + ")", roomIds.toArray());
    Map<Long, Kv> groups = new HashMap<>();
    for (Kv g : Db.findMaps("select * from im_room_group where is_del=0 and room_id in (" + in + ")", roomIds.toArray())) {
      groups.put(g.getLong("room_id"), g);
    }
    Map<Long, Kv> members = new HashMap<>();
    String memberSql = """
        select g.room_id, m.* from im_group_member m join im_room_group g on g.id=m.group_id
        where m.uid=? and m.is_del=0 and g.room_id in (%s)
        """.formatted(in);
    List<Object> memberParams = new ArrayList<>();
    memberParams.add(uid);
    memberParams.addAll(roomIds);
    for (Kv m : Db.findMaps(memberSql, memberParams.toArray())) {
      members.put(m.getLong("room_id"), m);
    }
    Map<Long, Kv> friendUsers = new HashMap<>();
    String friendSql = """
        select f.room_id, u.id, u.name, u.avatar, u.account from im_room_friend f
        join im_user u on u.id = if(f.uid1=?, f.uid2, f.uid1)
        where f.room_id in (%s)
        """.formatted(in);
    List<Object> friendParams = new ArrayList<>();
    friendParams.add(uid);
    friendParams.addAll(roomIds);
    for (Kv f : Db.findMaps(friendSql, friendParams.toArray())) {
      friendUsers.put(f.getLong("room_id"), f);
    }
    List<Long> lastMsgIds = new ArrayList<>();
    for (Kv room : rooms) {
      if (room.getLong("last_msg_id") != null) {
        lastMsgIds.add(room.getLong("last_msg_id"));
      }
    }
    Map<Long, MessageRecord> messages = new HashMap<>();
    if (!lastMsgIds.isEmpty()) {
      String sql = "select * from im_message where id in (" + Values.placeholders(lastMsgIds.size()) + ")";
      for (Kv row : Db.findMaps(sql, lastMsgIds.toArray())) {
        MessageRecord m = MessageRecord.from(row);
        messages.put(m.getId(), m);
      }
    }
    Map<Long, Integer> unread = unreadCounts(uid, contactMap.values());

    for (Kv room : rooms) {
      long roomId = room.getLong("id");
      Integer type = Values.toInt(room.get("type"));
      ChatRoomResp resp = new ChatRoomResp();
      Kv contact = contactMap.get(roomId);
      if (contact != null) {
        resp.setId(contact.getLong("id"));
        resp.setHide(Values.toBool(contact.get("hide")));
        resp.setShield(Values.toBool(contact.get("shield")));
        resp.setMuteNotification(Values.toInt(contact.get("mute_notification")));
        resp.setTop(Values.toBool(contact.get("top")));
      } else {
        resp.setHide(true);
        resp.setShield(true);
        resp.setMuteNotification(2);
        resp.setTop(false);
      }
      resp.setRoomId(roomId);
      resp.setType(type);
      resp.setHotFlag(Values.toInt(room.get("hot_flag")));
      resp.setActiveTime(Values.time(room.get("active_time")));
      if (Objects.equals(type, ImConst.ROOM_TYPE_GROUP)) {
        Kv group = groups.get(roomId);
        if (group == null) {
          continue;
        }
        resp.setDetailId(group.getLong("id"));
        resp.setAvatar(group.getStr("avatar"));
        resp.setAccount(group.getStr("account"));
        Kv member = members.get(roomId);
        if (member != null) {
          resp.setName(group.getStr("name"));
          resp.setMyName(member.getStr("my_name"));
          resp.setRemark(member.getStr("remark"));
          resp.setOperate(Values.toInt(member.get("role_id")));
        } else {
          resp.setName("会话异常");
          resp.setMyName("会话异常");
          resp.setRemark("会话异常");
          resp.setOperate(0);
        }
      } else {
        Kv friend = friendUsers.get(roomId);
        if (friend == null) {
          continue;
        }
        resp.setDetailId(friend.getLong("id"));
        resp.setOperate(0);
        resp.setName(friend.getStr("name"));
        resp.setAvatar(friend.getStr("avatar"));
        resp.setAccount(friend.getStr("account"));
      }
      MessageRecord message = messages.get(room.getLong("last_msg_id"));
      if (Boolean.TRUE.equals(resp.getShield())) {
        resp.setText("您已屏蔽该会话");
      } else if (message != null) {
        MsgHandler handler = MsgHandlers.find(message.getType());
        resp.setText(handler == null ? message.getContent() : handler.showContact(message));
      }
      boolean shield = Boolean.TRUE.equals(resp.getShield());
      resp.setUnreadCount(shield ? 0 : unread.getOrDefault(roomId, 0));
      result.add(resp);
    }
    result.sort(Comparator.comparing(ChatRoomResp::getActiveTime, Comparator.nullsLast(Comparator.reverseOrder())));
    return result;
  }

  private Map<Long, Integer> unreadCounts(long uid, Collection<Kv> contacts) {
    Map<Long, Integer> result = new HashMap<>();
    if (contacts.isEmpty()) {
      return result;
    }
    StringBuilder sql = new StringBuilder("select room_id, count(*) cnt from im_message where status=0 and (");
    List<Object> params = new ArrayList<>();
    boolean first = true;
    for (Kv contact : contacts) {
      if (!first) {
        sql.append(" or ");
      }
      first = false;
      sql.append("(room_id=? and create_time>? and from_uid<>?)");
      params.add(contact.getLong("room_id"));
      params.add(contact.get("read_time"));
      params.add(uid);
    }
    sql.append(") group by room_id");
    for (Kv row : Db.findMaps(sql.toString(), params.toArray())) {
      result.put(row.getLong("room_id"), Values.toInt(row.get("cnt")));
    }
    return result;
  }
}
