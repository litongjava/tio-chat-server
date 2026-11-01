package nexus.io.tio.chat.service.im;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.im.chat.ChatMessageReq;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.service.im.chat.MessageService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.chat.ws.PushService;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

public class ContactService {

  /**
   * 创建会话，已存在时恢复显示
   */
  public void createContact(long uid, long roomId, long tenantId) {
    String sql = """
        insert into im_contact (id, room_id, uid, is_del, tenant_id, create_by)
        values (?, ?, ?, 0, ?, ?)
        on duplicate key update is_del = 0, hide = 0
        """;
    Db.update(sql, SnowflakeIdUtils.id(), roomId, uid, tenantId, uid);
  }

  /**
   * 新消息到达时刷新或创建会话
   */
  public void refreshOrCreateActiveTime(long roomId, List<Long> uids, long msgId, LocalDateTime activeTime,
      long tenantId) {
    String sql = """
        insert into im_contact (id, room_id, uid, last_msg_id, active_time, is_del, tenant_id)
        values (?, ?, ?, ?, ?, 0, ?)
        on duplicate key update is_del = 0, hide = 0,
          last_msg_id = values(last_msg_id), active_time = values(active_time)
        """;
    for (Long uid : uids) {
      Db.update(sql, SnowflakeIdUtils.id(), roomId, uid, msgId, activeTime, tenantId);
    }
  }

  public Kv get(long uid, long roomId) {
    return Db.findFirstMap("select * from im_contact where uid=? and room_id=? and is_del=0", uid, roomId);
  }

  public boolean setTop(long uid, Long roomId, Boolean top) {
    Kv contact = get(uid, roomId);
    if (contact == null) {
      return false;
    }
    return Db.update("update im_contact set top=? where id=?", Values.isTrue(top), contact.getLong("id")) > 0;
  }

  public boolean setHide(long uid, Long roomId, Boolean hide) {
    return Db.update("update im_contact set hide=? where uid=? and room_id=?", Values.isTrue(hide), uid, roomId) > 0;
  }

  /**
   * 免打扰设置，同步到本人的其他设备
   */
  public boolean setNotification(long uid, Long roomId, Integer type) {
    Kv contact = get(uid, roomId);
    if (contact == null) {
      return false;
    }
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.ROOM_NOTIFICATION,
        Kv.by("roomId", roomId).set("type", type)), uid);
    return Db.update("update im_contact set mute_notification=? where id=?", type, contact.getLong("id")) > 0;
  }

  /**
   * 屏蔽会话：群聊屏蔽自己在群内的接收，单聊双向标记屏蔽
   */
  public boolean setShield(long uid, Long roomId, Boolean state) {
    Kv contact = get(uid, roomId);
    if (contact == null) {
      return false;
    }
    boolean shield = Values.isTrue(state);
    RoomQueryService roomQueryService = Aop.get(RoomQueryService.class);
    Kv room = roomQueryService.getRoom(roomId);
    String name;
    if (roomQueryService.isGroup(room)) {
      Kv group = roomQueryService.getRoomGroupByRoomId(roomId);
      name = group == null ? "" : group.getStr("name");
      Db.update("update im_group_member set de_friend=? where group_id=? and uid=?", shield, group.getLong("id"), uid);
    } else {
      Kv friend = roomQueryService.getRoomFriendByRoomId(roomId);
      boolean u1 = Objects.equals(friend.getLong("uid1"), uid);
      Db.update("update im_room_friend set " + (u1 ? "de_friend1" : "de_friend2") + "=? where id=?", shield,
          friend.getLong("id"));
      long other = u1 ? friend.getLong("uid2") : friend.getLong("uid1");
      UserSummary summary = Aop.get(UserSummaryService.class).get(other);
      name = summary == null ? "" : summary.getName();
    }
    String text = String.format("你已%s屏蔽来自%s的消息", shield ? "" : "解除", name);
    Aop.get(PushService.class).push(WsBaseResp.of(shield ? WsRespType.SHIELD : WsRespType.UNBLOCK, text), uid);
    return Db.update("update im_contact set shield=? where id=?", shield, contact.getLong("id")) > 0;
  }

  /**
   * 创建临时会话：对方允许时建立单聊房间并发送一条打招呼消息
   */
  public boolean createTempContact(long uid, Long toUid) {
    ChatErrors.require(toUid != null, "toUid不能为空");
    Kv privacy = Db.findFirstMap("select * from im_user_privacy where uid=? and is_del=0", toUid);
    if (privacy != null && (Values.isTrue(privacy.get("is_private")) || !Values.isTrue(privacy.get("allow_temp_session")))) {
      throw ChatErrors.of("对方设置了不接受临时会话");
    }
    Kv exists = Db.findFirstMap("select is_temp from im_user_friend where uid=? and friend_uid=? and is_del=0", uid, toUid);
    if (exists != null && Values.isTrue(exists.get("is_temp"))) {
      throw ChatErrors.of("已存在临时会话，请勿重复创建");
    }
    long roomId = Aop.get(RoomService.class).createFriendRoom(uid, toUid, 1L);
    Aop.get(FriendService.class).createFriend(roomId, uid, toUid, 1L);
    ChatMessageReq req = ChatMessageReq.of(roomId, MsgType.TEXT, Kv.by("content", "我们已经成为好友了，开始聊天吧"));
    req.setPushMessage(true);
    req.setSkip(true);
    Aop.get(MessageService.class).send(req, uid);
    return true;
  }
}
