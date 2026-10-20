package nexus.io.tio.chat.service.im.chat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.redis.Redis;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.consts.RedisKeys;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.common.CursorPageBaseResp;
import nexus.io.tio.chat.model.im.chat.ChatMessageResp;
import nexus.io.tio.chat.model.im.chat.MessageRecord;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.service.im.RoomQueryService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.chat.ws.PushService;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 消息查询、同步、标记、撤回与已读
 */
public class ChatService {

  public CursorPageBaseResp<ChatMessageResp> page(long roomId, String cursor, int pageSize, boolean skip, long uid) {
    MessageService messageService = Aop.get(MessageService.class);
    if (!skip) {
      messageService.checkRoomAccess(false, false, roomId, uid);
    }
    Long upperBound = lastVisibleMsgId(roomId, uid);
    List<Object> params = new ArrayList<>();
    StringBuilder where = new StringBuilder(" from im_message where room_id=? and status=0 and is_del=0");
    params.add(roomId);
    if (upperBound != null) {
      where.append(" and id<=?");
      params.add(upperBound);
    }
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
    List<MessageRecord> messages = new ArrayList<>();
    for (Kv row : rows) {
      messages.add(MessageRecord.from(row));
    }
    String nextCursor = String.valueOf(messages.get(messages.size() - 1).getId());
    List<ChatMessageResp> list = filterBlack(messageService.buildResp(messages, uid));
    return new CursorPageBaseResp<>(nextCursor, rows.size() != pageSize, list, total);
  }

  /**
   * 被移出群聊的成员只能看到移出前的消息
   */
  private Long lastVisibleMsgId(long roomId, long uid) {
    RoomQueryService roomQueryService = Aop.get(RoomQueryService.class);
    if (roomQueryService.isMember(roomId, uid)) {
      return null;
    }
    return Db.queryLong("select last_msg_id from im_contact where uid=? and room_id=?", uid, roomId);
  }

  /**
   * 登录同步：msgIds 为空时拉取最近 14 天可访问房间的消息
   */
  public List<ChatMessageResp> list(List<Long> msgIds, boolean async, long uid) {
    List<MessageRecord> messages = new ArrayList<>();
    if (msgIds == null || msgIds.isEmpty()) {
      List<Long> roomIds = accessibleRoomIds(uid);
      if (roomIds.isEmpty()) {
        return new ArrayList<>();
      }
      LocalDateTime start = LocalDateTime.now().minusDays(14);
      String sql = "select * from im_message where is_del=0 and create_time>=? and room_id in ("
          + Values.placeholders(roomIds.size()) + ") order by id";
      List<Object> params = new ArrayList<>();
      params.add(start);
      params.addAll(roomIds);
      for (Kv row : Db.findMaps(sql, params.toArray())) {
        messages.add(MessageRecord.from(row));
      }
    } else {
      String sql = "select * from im_message where id in (" + Values.placeholders(msgIds.size()) + ")";
      for (Kv row : Db.findMaps(sql, msgIds.toArray())) {
        messages.add(MessageRecord.from(row));
      }
    }
    Map<Long, List<MessageRecord>> grouped = new LinkedHashMap<>();
    for (MessageRecord m : messages) {
      grouped.computeIfAbsent(m.getRoomId(), k -> new ArrayList<>()).add(m);
    }
    MessageService messageService = Aop.get(MessageService.class);
    List<ChatMessageResp> result = new ArrayList<>();
    for (List<MessageRecord> roomMessages : grouped.values()) {
      result.addAll(messageService.buildResp(roomMessages, uid));
    }
    if ((msgIds == null || msgIds.isEmpty()) && !async) {
      for (Map.Entry<Long, List<MessageRecord>> entry : grouped.entrySet()) {
        List<MessageRecord> roomMessages = entry.getValue();
        MessageRecord last = roomMessages.get(roomMessages.size() - 1);
        Db.update("update im_contact set last_msg_id=?, active_time=? where uid=? and room_id=?", last.getId(),
            last.getCreateTime(), uid, entry.getKey());
      }
    }
    return filterBlack(result);
  }

  public List<Long> accessibleRoomIds(long uid) {
    String sql = """
        select g.room_id from im_group_member m join im_room_group g on g.id=m.group_id
        where m.uid=? and m.is_del=0 and m.de_friend=0
        union
        select room_id from im_user_friend where uid=? and is_del=0
        """;
    return Db.query(sql, uid, uid);
  }

  private List<ChatMessageResp> filterBlack(List<ChatMessageResp> list) {
    List<String> black = Db.query("select target from im_black where type=2 and is_del=0 and deadline>now()");
    if (black.isEmpty()) {
      return list;
    }
    list.removeIf(r -> black.contains(r.getFromUser().getUid()));
    return list;
  }

  /**
   * 消息表情标记，actType=1 标记，actType=2 取消
   */
  public void mark(long uid, Long msgId, Integer markType, Integer actType) {
    ChatErrors.require(markType != null && markType >= 1 && markType <= MessageService.MARK_TYPE_MAX, "参数校验失败");
    ChatErrors.require(actType != null && (actType == 1 || actType == 2), "动作类型 1确认 2取消");
    MessageRecord message = Aop.get(MessageService.class).getById(msgId);
    if (message == null) {
      return;
    }
    Kv old = Db.findFirstMap("select * from im_message_mark where uid=? and msg_id=? and type=?", uid, msgId, markType);
    if (old == null && actType == 2) {
      return;
    }
    int status = actType == 1 ? 0 : 1;
    if (old == null) {
      Row row = Row.by("id", SnowflakeIdUtils.id()).set("msg_id", msgId).set("uid", uid).set("type", markType)
          .set("status", status).set("create_by", uid).set("tenant_id", 1L);
      DbRows.save("im_message_mark", row);
    } else {
      Db.update("update im_message_mark set status=? where id=?", status, old.getLong("id"));
    }
    Long count = Db.queryLong("select count(*) from im_message_mark where msg_id=? and type=? and status=0", msgId, markType);
    Kv item = Kv.by("uid", String.valueOf(uid)).set("msgId", String.valueOf(msgId)).set("markType", markType)
        .set("markCount", count.intValue()).set("actType", actType);
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.MSG_MARK_ITEM, Kv.by("markList", List.of(item))),
        roomPeople(uid, message.getRoomId()));
  }

  /**
   * 撤回：本人 2 分钟内可撤回，群主和管理员可撤回任意消息
   */
  public void recall(long uid, Long msgId) {
    MessageRecord message = Aop.get(MessageService.class).getById(msgId);
    ChatErrors.require(message != null, "消息有误");
    ChatErrors.require(!Objects.equals(message.getType(), MsgType.RECALL), "消息无法撤回");
    RoomQueryService roomQueryService = Aop.get(RoomQueryService.class);
    boolean admin = false;
    if (roomQueryService.isGroup(roomQueryService.getRoom(message.getRoomId()))) {
      Kv member = roomQueryService.getMember(message.getRoomId(), uid);
      Integer role = member == null ? null : Values.toInt(member.get("role_id"));
      admin = Objects.equals(role, ImConst.GROUP_ROLE_LEADER) || Objects.equals(role, ImConst.GROUP_ROLE_MANAGER);
    }
    if (!admin) {
      ChatErrors.require(Objects.equals(uid, message.getFromUid()), "抱歉,您没有权限");
      long minutes = Duration.between(message.getCreateTime(), LocalDateTime.now()).toMinutes();
      ChatErrors.require(minutes < 2, "超过2分钟的消息不能撤回");
    }
    Map<String, Object> extra = message.getExtra();
    long now = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    extra.put("recall", Kv.by("recallUid", uid).set("recallTime", now));
    Db.update("update im_message set type=?, extra=? where id=?", MsgType.RECALL, JsonUtils.toJson(extra), msgId);
    Kv data = Kv.by("msgId", String.valueOf(msgId)).set("roomId", String.valueOf(message.getRoomId()))
        .set("recallUid", String.valueOf(uid));
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.MSG_RECALL, data), roomPeople(uid, message.getRoomId()));
  }

  public List<Long> roomPeople(long uid, long roomId) {
    RoomQueryService roomQueryService = Aop.get(RoomQueryService.class);
    if (roomQueryService.isGroup(roomQueryService.getRoom(roomId))) {
      return roomQueryService.groupMemberUids(roomId, true);
    }
    return roomQueryService.receiverUids(roomId);
  }

  /**
   * 已读或未读成员分页，searchType=1 已读，其他为未读
   */
  public CursorPageBaseResp<Kv> readPage(long uid, Long msgId, Long searchType, String cursor, int pageSize) {
    MessageRecord message = Aop.get(MessageService.class).getById(msgId);
    ChatErrors.require(message != null, "消息id有误");
    ChatErrors.require(Objects.equals(uid, message.getFromUid()), "只能查看自己的消息");
    boolean read = searchType != null && searchType == 1;
    List<Object> params = new ArrayList<>();
    StringBuilder where = new StringBuilder(" from im_contact where room_id=? and hide=0 and uid<>? and read_time"
        + (read ? ">=?" : "<?"));
    params.add(message.getRoomId());
    params.add(message.getFromUid());
    params.add(message.getCreateTime());
    long total = Db.queryLong("select count(*)" + where, params.toArray());
    if (cursor != null && !cursor.isBlank() && !"null".equals(cursor)) {
      where.append(" and read_time<?");
      params.add(LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(cursor)), ZoneId.systemDefault()));
    }
    params.add(pageSize);
    List<Kv> rows = Db.findMaps("select uid, read_time" + where + " order by read_time desc limit ?", params.toArray());
    if (rows.isEmpty()) {
      return CursorPageBaseResp.empty();
    }
    List<Kv> list = new ArrayList<>();
    for (Kv row : rows) {
      list.add(Kv.by("uid", row.getLong("uid")));
    }
    LocalDateTime lastTime = Values.time(rows.get(rows.size() - 1).get("read_time"));
    String next = String.valueOf(lastTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    return new CursorPageBaseResp<>(next, rows.size() != pageSize, list, total);
  }

  public List<Kv> readInfo(long uid, List<Long> msgIds) {
    List<Kv> result = new ArrayList<>();
    if (msgIds == null || msgIds.isEmpty()) {
      return result;
    }
    MessageService messageService = Aop.get(MessageService.class);
    for (Long msgId : msgIds) {
      MessageRecord message = messageService.getById(msgId);
      if (message == null) {
        continue;
      }
      ChatErrors.require(Objects.equals(uid, message.getFromUid()), "只能查询自己发送的消息");
      Long total = Db.queryLong("select count(*) from im_contact where room_id=? and hide=0", message.getRoomId());
      Long readCount = Db.queryLong(
          "select count(*) from im_contact where room_id=? and hide=0 and uid<>? and read_time>=?", message.getRoomId(),
          message.getFromUid(), message.getCreateTime());
      result.add(Kv.by("msgId", msgId).set("readCount", readCount.intValue())
          .set("unReadCount", (int) (total - readCount - 1)));
    }
    return result;
  }

  public void read(long uid, Long roomId) {
    ChatErrors.require(roomId != null, "房间号不能为空");
    Db.update("update im_contact set read_time=now(3) where uid=? and room_id=?", uid, roomId);
  }

  /**
   * 客户端确认收到消息：移出待确认集合并刷新会话
   */
  public void ack(long uid, Long msgId) {
    if (msgId == null) {
      return;
    }
    Redis.call(jedis -> jedis.srem(RedisKeys.PASSAGE_MSG + uid, String.valueOf(msgId)));
    MessageRecord message = Aop.get(MessageService.class).getById(msgId);
    if (message == null || !Aop.get(RoomQueryService.class).isMember(message.getRoomId(), uid)) {
      return;
    }
    String sql = """
        insert into im_contact (id, room_id, uid, last_msg_id, active_time, is_del, tenant_id)
        values (?, ?, ?, ?, ?, 0, 1)
        on duplicate key update is_del=0, hide=0, last_msg_id=values(last_msg_id), active_time=values(active_time)
        """;
    Db.update(sql, SnowflakeIdUtils.id(), message.getRoomId(), uid, message.getId(), message.getCreateTime());
  }
}
