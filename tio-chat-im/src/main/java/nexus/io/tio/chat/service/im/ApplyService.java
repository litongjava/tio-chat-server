package nexus.io.tio.chat.service.im;

import java.util.List;
import java.util.Objects;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.consts.NoticeConst;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.im.chat.ChatMessageReq;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.service.im.chat.MessageService;
import nexus.io.tio.chat.service.im.group.GroupMemberChangeService;
import nexus.io.tio.chat.service.im.group.GroupService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.chat.ws.PushService;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 好友申请与加群申请
 */
public class ApplyService {

  /**
   * 发起好友申请；对方已向我申请时直接同意
   */
  public Kv applyFriend(long uid, Long targetUid, String msg) {
    ChatErrors.require(targetUid != null, "targetUid不能为空");
    ChatErrors.require(!Aop.get(FriendService.class).isFriend(uid, targetUid), "你们已经是好友了");
    Kv self = approving(uid, targetUid, true);
    if (self != null) {
      return null;
    }
    Kv reverse = approving(targetUid, uid, false);
    if (reverse != null) {
      handle(uid, reverse.getLong("id"), NoticeConst.STATUS_ACCEPTED);
      return null;
    }
    long applyId = SnowflakeIdUtils.id();
    Row apply = Row.by("id", applyId).set("uid", uid).set("type", ImConst.ROOM_TYPE_FRIEND).set("room_id", 0L)
        .set("target_id", targetUid).set("msg", msg).set("status", NoticeConst.STATUS_UNTREATED)
        .set("read_status", NoticeConst.UNREAD).set("deleted", NoticeConst.APPLY_NORMAL).set("apply_for", 0)
        .set("join_channel", 0).set("create_by", uid).set("tenant_id", 1L);
    DbRows.save("im_user_apply", apply);
    NoticeService noticeService = Aop.get(NoticeService.class);
    noticeService.create(ImConst.ROOM_TYPE_FRIEND, NoticeConst.EVENT_ADD_ME, uid, targetUid, applyId, targetUid, 0L, msg);
    noticeService.create(ImConst.ROOM_TYPE_FRIEND, NoticeConst.EVENT_FRIEND_APPLY, uid, uid, applyId, targetUid, 0L, msg);
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.NEW_APPLY, noticeService.unread(uid, targetUid)), targetUid);
    return toApply(Db.findFirstMap("select * from im_user_apply where id=?", applyId));
  }

  private Kv approving(long uid, long targetUid, boolean initiator) {
    String deleted = initiator ? "(1,3)" : "(2,3)";
    String sql = "select * from im_user_apply where uid=? and target_id=? and status=0 and type=2 and is_del=0 and deleted not in "
        + deleted + " limit 1";
    return Db.findFirstMap(sql, uid, targetUid);
  }

  private Kv toApply(Kv row) {
    return Kv.by("id", row.getLong("id")).set("uid", row.getLong("uid")).set("type", Values.toInt(row.get("type")))
        .set("roomId", row.getLong("room_id")).set("targetId", row.getLong("target_id")).set("msg", row.getStr("msg"))
        .set("status", Values.toInt(row.get("status"))).set("applyFor", Values.toBool(row.get("apply_for")))
        .set("joinChannel", Values.toInt(row.get("join_channel"))).set("readStatus", Values.toInt(row.get("read_status")))
        .set("deleted", Values.toInt(row.get("deleted"))).set("createTime", Values.time(row.get("create_time")));
  }

  /**
   * 申请加群：type=2 为扫码进群
   */
  public boolean applyGroup(long uid, String account, Integer type, String msg) {
    Kv group = Db.findFirstMap("select * from im_room_group where account=? and is_del=0", account);
    if (group == null) {
      return false;
    }
    boolean scan = Objects.equals(type, 2);
    if (scan && !Values.isTrue(group.get("allow_scan_enter"))) {
      return false;
    }
    long roomId = group.getLong("room_id");
    ChatErrors.require(Aop.get(RoomQueryService.class).getMember(roomId, uid) == null, "你已经加入群聊");
    UserSummary user = Aop.get(UserSummaryService.class).get(uid);
    String applyMsg = String.format("用户%s申请加入群聊%s", user.getName(), group.getStr("name"));
    String content = msg == null || msg.isBlank() ? (scan ? "扫码加入群聊" : "申请加入群聊") : msg;
    long applyId = SnowflakeIdUtils.id();
    Row apply = Row.by("id", applyId).set("uid", uid).set("type", ImConst.ROOM_TYPE_GROUP).set("room_id", roomId)
        .set("target_id", uid).set("msg", applyMsg).set("status", NoticeConst.STATUS_UNTREATED)
        .set("read_status", NoticeConst.UNREAD).set("deleted", 0).set("apply_for", 1).set("join_channel", scan ? 2 : 3)
        .set("create_by", uid).set("tenant_id", 1L);
    DbRows.save("im_user_apply", apply);
    NoticeService noticeService = Aop.get(NoticeService.class);
    noticeService.create(ImConst.ROOM_TYPE_GROUP, NoticeConst.EVENT_GROUP_APPLY, uid, uid, applyId, uid, roomId, content);
    for (Long manager : Aop.get(GroupService.class).managerUids(group.getLong("id"))) {
      noticeService.create(ImConst.ROOM_TYPE_GROUP, NoticeConst.EVENT_GROUP_APPLY, uid, manager, applyId, uid, roomId, content);
    }
    return true;
  }

  /**
   * 审批：1 同意、2 拒绝、3 忽略
   */
  public void handle(long uid, Long applyId, Integer state) {
    Kv apply = Db.findFirstMap("select * from im_user_apply where id=? and is_del=0", applyId);
    ChatErrors.require(apply != null, "无效的邀请");
    ChatErrors.require(state != null && state != NoticeConst.STATUS_UNTREATED, "无效的审批状态");
    ChatErrors.require(Objects.equals(Values.toInt(apply.get("status")), NoticeConst.STATUS_UNTREATED), "无效的审批");
    NoticeService noticeService = Aop.get(NoticeService.class);
    ChatErrors.require(noticeService.getByApplyId(uid, applyId) != null, "通知无法处理");
    if (state == NoticeConst.STATUS_REJECTED || state == NoticeConst.STATUS_IGNORE) {
      Db.update("update im_user_apply set status=? where id=?", state, applyId);
      noticeService.updateStatus(applyId, state, false, uid);
      return;
    }
    ChatErrors.require(state == NoticeConst.STATUS_ACCEPTED, "无效的审批状态");
    if (Objects.equals(Values.toInt(apply.get("type")), ImConst.ROOM_TYPE_FRIEND)) {
      acceptFriend(uid, apply);
    } else {
      acceptGroup(uid, apply);
    }
  }

  private void acceptFriend(long uid, Kv apply) {
    long applicant = apply.getLong("uid");
    long applyId = apply.getLong("id");
    Kv existing = Db.findFirstMap("select * from im_user_friend where uid=? and friend_uid=? and is_del=0", uid, applicant);
    boolean fromTemp = existing != null && Values.isTrue(existing.get("is_temp"));
    Long roomId = Db.txResult(() -> {
      Db.update("update im_user_apply set status=?, read_status=? where id=?", NoticeConst.STATUS_ACCEPTED, NoticeConst.READ,
          applyId);
      if (fromTemp) {
        Db.update("update im_user_friend set is_temp=0 where room_id=?", existing.getLong("room_id"));
        return existing.getLong("room_id");
      }
      long newRoomId = Aop.get(RoomService.class).createFriendRoom(uid, applicant, 1L);
      Aop.get(FriendService.class).createFriend(newRoomId, uid, applicant, 1L);
      return newRoomId;
    });
    if (!fromTemp) {
      ChatMessageReq req = ChatMessageReq.of(roomId, MsgType.TEXT, Kv.by("content", "我们已经成为好友了，开始聊天吧"));
      req.setPushMessage(true);
      req.setSkip(true);
      Aop.get(MessageService.class).send(req, uid);
    }
    Aop.get(NoticeService.class).updateStatus(applyId, NoticeConst.STATUS_ACCEPTED, false, uid);
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.REQUEST_APPROVAL_FRIEND, Kv.by("uid", uid)), applicant);
  }

  private void acceptGroup(long uid, Kv apply) {
    boolean applyFor = Values.isTrue(apply.get("apply_for"));
    long member = applyFor ? apply.getLong("uid") : apply.getLong("target_id");
    long roomId = apply.getLong("room_id");
    GroupService groupService = Aop.get(GroupService.class);
    Kv group = groupService.requireGroup(roomId);
    if (applyFor) {
      Integer role = groupService.role(uid, group.getLong("id"));
      ChatErrors.require(Objects.equals(role, ImConst.GROUP_ROLE_LEADER) || Objects.equals(role, ImConst.GROUP_ROLE_MANAGER),
          "无审批权限");
    }
    if (Aop.get(RoomQueryService.class).getMember(roomId, member) != null) {
      UserSummary user = Aop.get(UserSummaryService.class).get(apply.getLong("target_id"));
      throw ChatErrors.of(String.format("%s已经在%s里", user == null ? "" : user.getName(), group.getStr("name")));
    }
    Db.update("update im_user_apply set status=? where id=?", NoticeConst.STATUS_ACCEPTED, apply.getLong("id"));
    GroupMemberChangeService changeService = Aop.get(GroupMemberChangeService.class);
    changeService.join(roomId, group.getLong("id"), List.of(member), uid);
    Aop.get(NoticeService.class).updateStatus(apply.getLong("id"), NoticeConst.STATUS_ACCEPTED, true, uid);
    changeService.sendJoinMessage(roomId, apply.getLong("uid"), List.of(member), applyFor,
        Values.toInt(apply.get("join_channel")));
  }

  public void delete(long uid, Long applyId) {
    Kv apply = Db.findFirstMap("select * from im_user_apply where id=? and is_del=0", applyId);
    ChatErrors.require(apply != null, "不存在申请记录");
    ChatErrors.require(Objects.equals(Values.toInt(apply.get("status")), NoticeConst.STATUS_UNTREATED), "对方已是您的好友");
    int deleted = Objects.equals(apply.getLong("uid"), uid) ? NoticeConst.APPLY_DELETED : NoticeConst.TARGET_DELETED;
    int current = Values.toInt(apply.get("deleted")) == null ? 0 : Values.toInt(apply.get("deleted"));
    if (current == deleted) {
      return;
    }
    if (current != NoticeConst.APPLY_NORMAL) {
      deleted = NoticeConst.ALL_DELETED;
    }
    Db.update("update im_user_apply set deleted=? where id=?", deleted, applyId);
  }
}
