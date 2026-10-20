package nexus.io.tio.chat.service.im.group;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.common.IPageResp;
import nexus.io.tio.chat.model.im.chat.ChatMessageReq;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.service.im.RoomQueryService;
import nexus.io.tio.chat.service.im.chat.MessageService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.chat.ws.PushService;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 群公告：发布、编辑、删除、已读统计
 */
public class AnnouncementService {
  private static final long CONTRIBUTOR_ITEM_ID = 6L;

  private boolean canManage(long uid, long roomId) {
    Kv group = Aop.get(RoomQueryService.class).getRoomGroupByRoomId(roomId);
    if (group == null) {
      return false;
    }
    Integer role = Aop.get(GroupService.class).role(uid, group.getLong("id"));
    return Objects.equals(role, ImConst.GROUP_ROLE_LEADER) || Objects.equals(role, ImConst.GROUP_ROLE_MANAGER);
  }

  private Kv entity(Kv row) {
    return Kv.by("id", row.getLong("id")).set("roomId", row.getLong("room_id")).set("uid", row.getLong("uid"))
        .set("content", row.getStr("content")).set("top", Values.toBool(row.get("top")))
        .set("createTime", Values.time(row.get("create_time"))).set("updateTime", Values.time(row.get("update_time")))
        .set("createBy", row.getLong("create_by")).set("updateBy", row.getLong("update_by"));
  }

  public boolean push(long uid, Long roomId, String content, Boolean top) {
    if (!canManage(uid, roomId)) {
      return false;
    }
    List<Long> members = Aop.get(RoomQueryService.class).groupMemberUids(roomId, false);
    if (members.isEmpty()) {
      return false;
    }
    long id = SnowflakeIdUtils.id();
    LocalDateTime now = LocalDateTime.now();
    Db.tx(() -> {
      if (Values.isTrue(top)) {
        Db.update("update im_announcements set top=0 where room_id=? and top=1", roomId);
      }
      DbRows.save("im_announcements", Row.by("id", id).set("room_id", roomId).set("uid", uid).set("content", content)
          .set("top", Values.isTrue(top)).set("create_by", uid).set("create_time", now).set("update_time", now)
          .set("tenant_id", 1L));
      for (Long member : members) {
        DbRows.save("im_announcements_read_records", Row.by("id", SnowflakeIdUtils.id()).set("announcements_id", id)
            .set("uid", member).set("is_check", 0).set("create_by", uid).set("tenant_id", 1L));
      }
      return true;
    });
    sendNoticeMessage(uid, roomId, id, content, Values.isTrue(top), now);
    return true;
  }

  private void sendNoticeMessage(long uid, long roomId, long id, String content, boolean top, LocalDateTime time) {
    long millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    Kv body = Kv.by("id", id).set("roomId", roomId).set("uid", uid).set("content", content).set("createTime", millis)
        .set("top", top);
    ChatMessageReq req = ChatMessageReq.of(roomId, MsgType.NOTICE, body);
    req.setSkip(true);
    Aop.get(MessageService.class).send(req, uid);
  }

  public boolean edit(long uid, Long id, Long roomId, String content, Boolean top) {
    if (!canManage(uid, roomId)) {
      return false;
    }
    Kv old = Db.findFirstMap("select * from im_announcements where id=? and is_del=0", id);
    if (old == null) {
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    if (Values.isTrue(top)) {
      Db.update("update im_announcements set top=0 where room_id=? and top=1", roomId);
    }
    boolean ok = Db.update("update im_announcements set content=?, top=?, update_time=?, update_by=? where id=?", content,
        Values.isTrue(top), now, uid, id) > 0;
    if (ok) {
      sendNoticeMessage(uid, roomId, id, content, Values.isTrue(top), now);
    }
    return ok;
  }

  public IPageResp<Kv> list(Long roomId, long current, long size) {
    long total = Db.queryLong("select count(*) from im_announcements where room_id=? and is_del=0", roomId);
    List<Kv> records = new ArrayList<>();
    String sql = "select * from im_announcements where room_id=? and is_del=0 order by top desc, create_time desc limit ?, ?";
    for (Kv row : Db.findMaps(sql, roomId, (current - 1) * size, size)) {
      records.add(entity(row));
    }
    return IPageResp.of(records, total, size, current);
  }

  public Kv get(long uid, Long roomId, Long id) {
    ChatErrors.require(Aop.get(RoomQueryService.class).isMember(roomId, uid), "您不是群成员");
    Kv row = Db.findFirstMap("select * from im_announcements where id=? and is_del=0", id);
    ChatErrors.require(row != null, "公告不存在");
    long count = readCount(id);
    return Kv.by("id", row.getLong("id")).set("roomId", row.getLong("room_id")).set("uid", row.getLong("uid"))
        .set("content", row.getStr("content")).set("createTime", Values.time(row.get("create_time"))).set("count", count)
        .set("top", Values.toBool(row.get("top")));
  }

  private long readCount(long id) {
    return Db.queryLong("select count(*) from im_announcements_read_records where announcements_id=? and is_check=1", id);
  }

  public boolean read(long uid, Long roomId, Long id) {
    boolean ok = Db.update("update im_announcements_read_records set is_check=1 where uid=? and announcements_id=?", uid, id) > 0;
    if (ok) {
      List<Long> members = Aop.get(RoomQueryService.class).groupMemberUids(roomId, true);
      Kv data = Kv.by("uid", uid).set("count", readCount(id));
      Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.ROOM_GROUP_NOTICE_READ_MSG, data), members);
    }
    return ok;
  }

  /**
   * 群主、管理员或持有贡献者徽章的成员可以删除公告
   */
  public boolean delete(long uid, Long id) {
    Kv row = Db.findFirstMap("select * from im_announcements where id=? and is_del=0", id);
    ChatErrors.require(row != null, "公告不存在");
    long contributor = Db.queryLong("select count(*) from im_user_backpack where uid=? and item_id=? and is_del=0", uid,
        CONTRIBUTOR_ITEM_ID);
    if (contributor == 0 && !canManage(uid, row.getLong("room_id"))) {
      return false;
    }
    return Db.update("update im_announcements set is_del=1 where id=?", id) > 0;
  }
}
