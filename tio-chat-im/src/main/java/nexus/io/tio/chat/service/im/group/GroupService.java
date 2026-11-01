package nexus.io.tio.chat.service.im.group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.consts.NoticeConst;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.common.PageBaseResp;
import nexus.io.tio.chat.model.im.chat.ChatMessageReq;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.service.im.ContactService;
import nexus.io.tio.chat.service.im.NoticeService;
import nexus.io.tio.chat.service.im.RoomQueryService;
import nexus.io.tio.chat.service.im.RoomService;
import nexus.io.tio.chat.service.im.UserSummaryService;
import nexus.io.tio.chat.service.im.chat.MessageService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.chat.ws.OnlineService;
import nexus.io.tio.chat.ws.PushService;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 群聊：创建、成员管理、管理员、群信息
 */
public class GroupService {
  private static final int MAX_MANAGE_COUNT = 3;
  /** im_room_group.name 列长度 */
  private static final int MAX_GROUP_NAME_LENGTH = 16;
  private static final String DEFAULT_GROUP_NAME_SUFFIX = "的群组";
  private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  private RoomQueryService rooms() {
    return Aop.get(RoomQueryService.class);
  }

  public Kv requireGroup(Long roomId) {
    ChatErrors.require(roomId != null, "房间号有误");
    Kv group = rooms().getRoomGroupByRoomId(roomId);
    ChatErrors.require(group != null, "群聊不存在");
    return group;
  }

  public Integer role(long uid, long groupId) {
    Integer role = Db.queryInt("select role_id from im_group_member where group_id=? and uid=? and is_del=0 limit 1",
        groupId, uid);
    return role == null ? 4 : role;
  }

  private boolean hasPower(Integer role) {
    return Objects.equals(role, ImConst.GROUP_ROLE_LEADER) || Objects.equals(role, ImConst.GROUP_ROLE_MANAGER);
  }

  public static String groupAccount(long id) {
    StringBuilder sb = new StringBuilder();
    long value = id;
    while (value > 0 && sb.length() < 19) {
      sb.append(BASE62.charAt((int) (value % BASE62.length())));
      value /= BASE62.length();
    }
    return EnvUtils.get("chat.group.account.prefix", "group_") + sb.reverse();
  }

  // ---------------------------------------------------------------- 查询

  public Kv memberResp(Kv group, long uid) {
    long roomId = group.getLong("room_id");
    Kv member = Db.findFirstMap("select * from im_group_member where group_id=? and uid=? and is_del=0", group.getLong("id"), uid);
    return Kv.by("groupId", group.getLong("id")).set("roomId", roomId).set("groupName", group.getStr("name"))
        .set("avatar", group.getStr("avatar")).set("onlineNum", Aop.get(OnlineService.class).groupOnlineCount(roomId))
        .set("roleId", role(uid, group.getLong("id"))).set("account", group.getStr("account"))
        .set("memberNum", (long) rooms().groupMemberUids(roomId, false).size())
        .set("remark", member == null ? "" : member.getStr("remark"))
        .set("myName", member == null ? "" : member.getStr("my_name"))
        .set("allowScanEnter", Values.toBool(group.get("allow_scan_enter")));
  }

  public Kv detail(long uid, Long roomId) {
    Kv group = requireGroup(roomId);
    return memberResp(group, uid);
  }

  public Kv info(Long roomId) {
    Kv group = Db.findFirstMap("select * from im_room_group where room_id=?", roomId);
    if (group == null) {
      return null;
    }
    return Kv.by("groupId", group.getLong("id")).set("roomId", group.getLong("room_id")).set("name", group.getStr("name"))
        .set("avatar", group.getStr("avatar")).set("account", group.getStr("account")).set("remark", null);
  }

  public List<Kv> search(String account) {
    List<Kv> result = new ArrayList<>();
    if (account == null || account.isBlank()) {
      return result;
    }
    for (Kv g : Db.findMaps("select * from im_room_group where account=? and is_del=0", account.trim())) {
      result.add(groupEntity(g));
    }
    return result;
  }

  private Kv groupEntity(Kv g) {
    return Kv.by("id", g.getLong("id")).set("roomId", g.getLong("room_id")).set("name", g.getStr("name"))
        .set("account", g.getStr("account")).set("avatar", g.getStr("avatar")).set("extJson", g.getStr("ext_json"))
        .set("allowScanEnter", Values.toBool(g.get("allow_scan_enter"))).set("createTime", Values.time(g.get("create_time")))
        .set("updateTime", Values.time(g.get("update_time")));
  }

  public List<Kv> groupList(long uid) {
    String sql = """
        select distinct g.*, r.update_time as room_update_time from im_room_group g
        join im_room r on g.room_id = r.id
        join im_group_member m on g.id = m.group_id and m.is_del = 0
        where m.uid = ? and g.is_del = 0
        order by room_update_time, g.id
        """;
    List<Kv> result = new ArrayList<>();
    for (Kv g : Db.findMaps(sql, uid)) {
      result.add(memberResp(g, uid));
    }
    return result;
  }

  public List<Kv> allGroups() {
    List<Kv> result = new ArrayList<>();
    String sql = "select g.* from im_room_group g join im_room r on g.room_id=r.id where g.is_del=0 order by r.update_time desc, g.id";
    for (Kv g : Db.findMaps(sql)) {
      Kv resp = memberResp(g, 0L);
      resp.remove("roleId");
      result.add(resp);
    }
    return result;
  }

  public PageBaseResp<Kv> groupPage(int pageNo, int pageSize, String groupName, String memberName) {
    long total = Db.queryLong("select count(*) from im_room_group where is_del=0");
    List<Kv> rows = Db.findMaps("select * from im_room_group where is_del=0 order by create_time desc limit ?, ?",
        (pageNo - 1) * pageSize, pageSize);
    List<Kv> list = new ArrayList<>();
    for (Kv g : rows) {
      if (groupName != null && !groupName.isBlank() && (g.getStr("name") == null || !g.getStr("name").contains(groupName.trim()))) {
        continue;
      }
      if (memberName != null && !memberName.isBlank()) {
        String sql = """
            select count(*) from im_group_member m join im_user u on u.id=m.uid
            where m.group_id=? and m.is_del=0 and u.name like ?
            """;
        if (Db.queryLong(sql, g.getLong("id"), "%" + memberName.trim() + "%") == 0) {
          continue;
        }
      }
      long roomId = g.getLong("room_id");
      list.add(Kv.by("roomId", roomId).set("groupId", g.getLong("id")).set("groupName", g.getStr("name"))
          .set("account", g.getStr("account")).set("avatar", g.getStr("avatar"))
          .set("allowScanEnter", Values.toBool(g.get("allow_scan_enter")))
          .set("onlineNum", Aop.get(OnlineService.class).groupOnlineCount(roomId))
          .set("memberNum", (long) rooms().groupMemberUids(roomId, false).size()));
    }
    boolean filtered = (groupName != null && !groupName.isBlank()) || (memberName != null && !memberName.isBlank());
    return PageBaseResp.init(pageNo, pageSize, filtered ? list.size() : total, list);
  }

  /**
   * 群成员完整信息
   */
  public List<Kv> listMember(Long roomId) {
    Kv room = rooms().getRoom(roomId);
    ChatErrors.require(room != null, "房间号有误");
    ChatErrors.require(rooms().isGroup(room), "当前房间非群聊");
    Kv group = requireGroup(roomId);
    List<Kv> members = Db.findMaps("select id, uid, role_id, my_name from im_group_member where group_id=? and is_del=0",
        group.getLong("id"));
    List<Long> uids = new ArrayList<>();
    for (Kv m : members) {
      uids.add(m.getLong("uid"));
    }
    Map<Long, UserSummary> users = Aop.get(UserSummaryService.class).getBatch(uids);
    Set<Long> online = Aop.get(OnlineService.class).onlineOf(uids);
    List<Kv> result = new ArrayList<>();
    for (Kv m : members) {
      long uid = m.getLong("uid");
      Kv item = Kv.by("id", m.getLong("id")).set("uid", String.valueOf(uid)).set("roleId", Values.toInt(m.get("role_id")))
          .set("myName", m.getStr("my_name"));
      UserSummary user = users.get(uid);
      if (user != null) {
        item.set("activeStatus", online.contains(uid) ? 1 : 2).set("lastOptTime", user.getLastOptTime())
            .set("name", user.getName()).set("avatar", user.getAvatar()).set("locPlace", user.getLocPlace())
            .set("account", user.getAccount()).set("userStateId", user.getUserStateId()).set("itemIds", user.getItemIds())
            .set("userType", user.getUserType()).set("wearingItemId", user.getWearingItemId())
            .set("linkedGitee", user.getLinkedGitee()).set("linkedGithub", user.getLinkedGithub())
            .set("linkedGitcode", user.getLinkedGitcode());
      }
      result.add(item);
    }
    return result;
  }

  /**
   * @ 成员列表
   */
  public List<Kv> memberList(Long roomId) {
    Kv room = rooms().getRoom(roomId);
    ChatErrors.require(room != null, "房间号有误");
    List<Long> uids;
    if (Values.isTrue(room.get("hot_flag"))) {
      uids = Db.query("select id from im_user where is_del=0 order by last_opt_time desc limit 1000");
    } else {
      uids = rooms().groupMemberUids(roomId, false);
    }
    List<Kv> result = new ArrayList<>();
    for (UserSummary user : Aop.get(UserSummaryService.class).getBatch(uids).values()) {
      result.add(Kv.by("uid", user.getUid()).set("name", user.getName()).set("avatar", user.getAvatar())
          .set("account", user.getAccount()).set("linkedGitee", user.getLinkedGitee())
          .set("linkedGithub", user.getLinkedGithub()).set("linkedGitcode", user.getLinkedGitcode()));
    }
    return result;
  }

  public PageBaseResp<Kv> memberPage(Long roomId, int pageNo, int pageSize) {
    Kv group = requireGroup(roomId);
    long total = Db.queryLong("select count(*) from im_group_member where group_id=? and is_del=0", group.getLong("id"));
    List<Kv> rows = Db.findMaps(
        "select * from im_group_member where group_id=? and is_del=0 order by role_id asc, create_time desc limit ?, ?",
        group.getLong("id"), (pageNo - 1) * pageSize, pageSize);
    if (rows.isEmpty()) {
      return PageBaseResp.empty();
    }
    List<Long> uids = new ArrayList<>();
    for (Kv m : rows) {
      uids.add(m.getLong("uid"));
    }
    Set<Long> online = Aop.get(OnlineService.class).onlineOf(uids);
    String in = Values.placeholders(uids.size());
    Map<Long, Kv> users = new HashMap<>();
    for (Kv u : Db.findMaps("select id, name, ip_info from im_user where id in (" + in + ")", uids.toArray())) {
      users.put(u.getLong("id"), u);
    }
    List<Kv> list = new ArrayList<>();
    for (Kv m : rows) {
      Kv user = users.get(m.getLong("uid"));
      if (user == null) {
        continue;
      }
      String myName = m.getStr("my_name");
      String ipInfo = user.getStr("ip_info");
      String locPlace = UserSummaryService.locPlace(ipInfo);
      String ip = "";
      if (ipInfo != null) {
        Object updateIp = JsonUtils.parseToMap(ipInfo, String.class, Object.class).get("updateIp");
        ip = updateIp == null ? "" : updateIp.toString();
      }
      list.add(Kv.by("uid", String.valueOf(m.getLong("uid")))
          .set("name", myName == null || myName.isEmpty() ? user.getStr("name") : myName)
          .set("roleId", Values.toInt(m.get("role_id"))).set("activeStatus", online.contains(m.getLong("uid")) ? 1 : 2)
          .set("locPlace", locPlace == null ? "" : locPlace).set("ipAddress", ip));
    }
    return PageBaseResp.init(pageNo, pageSize, total, list);
  }

  // ---------------------------------------------------------------- 变更

  /**
   * 创建群聊，返回房间号
   */
  public long create(long uid, String groupName, Collection<Long> uidList) {
    Set<Long> invitees = new LinkedHashSet<>(uidList == null ? List.of() : uidList);
    invitees.remove(ImConst.DEF_BOT_ID);
    invitees.remove(uid);
    Map<Long, UserSummary> users = Aop.get(UserSummaryService.class).getBatch(invitees);
    ChatErrors.require(users.size() > 1, "群聊人数应大于2人");
    Long selfGroups = Db.queryLong("select count(*) from im_group_member where uid=? and role_id=? and is_del=0", uid,
        ImConst.GROUP_ROLE_LEADER);
    ChatErrors.require(selfGroups < 20, "每个人只能创建20个群");
    requireGroupNameLength(groupName);
    UserSummary self = Aop.get(UserSummaryService.class).get(uid);
    long roomId = Db.txResult(() -> {
      long newRoomId = Aop.get(RoomService.class).createRoom(ImConst.ROOM_TYPE_GROUP, 1L);
      long groupId = SnowflakeIdUtils.id();
      String name = groupName == null || groupName.isBlank() ? defaultGroupName(self.getName()) : groupName;
      Row group = Row.by("id", groupId).set("room_id", newRoomId).set("account", groupAccount(SnowflakeIdUtils.id()))
          .set("name", name).set("avatar", self.getAvatar()).set("allow_scan_enter", 1).set("tenant_id", 1L)
          .set("create_by", uid);
      DbRows.save("im_room_group", group);
      RoomService roomService = Aop.get(RoomService.class);
      ContactService contactService = Aop.get(ContactService.class);
      roomService.createGroupMember(groupId, uid, ImConst.GROUP_ROLE_LEADER, 1L);
      contactService.createContact(uid, newRoomId, 1L);
      for (Long member : users.keySet()) {
        roomService.createGroupMember(groupId, member, ImConst.GROUP_ROLE_MEMBER, 1L);
        contactService.createContact(member, newRoomId, 1L);
      }
      return newRoomId;
    });
    OnlineService onlineService = Aop.get(OnlineService.class);
    onlineService.joinGroup(roomId, uid);
    for (Long member : users.keySet()) {
      onlineService.joinGroup(roomId, member);
    }
    Aop.get(GroupMemberChangeService.class).pushAdd(roomId, new ArrayList<>(users.keySet()), uid);
    return roomId;
  }

  /**
   * 邀请成员：生成待处理的邀请并通知被邀请人和管理员
   */
  public void invite(long uid, Long roomId, Collection<Long> uidList) {
    Set<Long> invitees = new LinkedHashSet<>(uidList == null ? List.of() : uidList);
    ChatErrors.require(!invitees.contains(ImConst.DEF_BOT_ID), "不能拉小管家进群!");
    Kv group = requireGroup(roomId);
    long groupId = group.getLong("id");
    ChatErrors.require(Db.findFirstMap("select id from im_group_member where group_id=? and uid=? and is_del=0", groupId, uid) != null,
        "您不是群成员");
    invitees.removeIf(id -> Db.queryLong("select id from im_group_member where group_id=? and uid=? and is_del=0", groupId, id) != null);
    invitees.removeIf(id -> Db.queryLong(
        "select id from im_user_apply where room_id=? and target_id=? and type=1 and status=0 and is_del=0 limit 1", roomId, id) != null);
    if (invitees.isEmpty()) {
      return;
    }
    UserSummary self = Aop.get(UserSummaryService.class).get(uid);
    String msg = String.format("%s邀请你加入%s", self.getName(), group.getStr("name"));
    NoticeService noticeService = Aop.get(NoticeService.class);
    List<Long> managers = managerUids(groupId);
    for (Long invitee : invitees) {
      long applyId = SnowflakeIdUtils.id();
      Row apply = Row.by("id", applyId).set("uid", uid).set("type", ImConst.ROOM_TYPE_GROUP).set("room_id", roomId)
          .set("target_id", invitee).set("msg", msg).set("status", NoticeConst.STATUS_UNTREATED)
          .set("read_status", NoticeConst.UNREAD).set("deleted", 0).set("apply_for", 0).set("join_channel", 1)
          .set("create_by", uid).set("tenant_id", 1L);
      DbRows.save("im_user_apply", apply);
      Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.NEW_APPLY, noticeService.unread(invitee, invitee)), invitee);
      noticeService.create(ImConst.ROOM_TYPE_GROUP, NoticeConst.EVENT_GROUP_INVITE_ME, uid, invitee, applyId, invitee,
          roomId, group.getStr("name"));
      for (Long manager : managers) {
        noticeService.create(ImConst.ROOM_TYPE_GROUP, NoticeConst.EVENT_GROUP_INVITE, uid, manager, applyId, invitee,
            roomId, group.getStr("name"));
      }
    }
  }

  public List<Long> managerUids(long groupId) {
    return Db.query("select uid from im_group_member where group_id=? and is_del=0 and role_id in (1,2)", groupId);
  }

  /**
   * 移除成员；群成员不超过 3 人时直接解散
   */
  public void removeMembers(long uid, Long roomId, Collection<Long> uidList) {
    ChatErrors.require(!Objects.equals(roomId, ImConst.DEF_ROOM_ID), "官方群聊无法移除");
    Kv group = requireGroup(roomId);
    long groupId = group.getLong("id");
    Integer selfRole = role(uid, groupId);
    ChatErrors.require(selfRole != 4, "用户不在群里");
    if (rooms().groupMemberUids(roomId, false).size() <= 3) {
      exit(true, uid, roomId);
      return;
    }
    NoticeService noticeService = Aop.get(NoticeService.class);
    for (Long removed : uidList) {
      Integer removedRole = role(removed, groupId);
      ChatErrors.require(!Objects.equals(removedRole, ImConst.GROUP_ROLE_LEADER), "不允许移除群主");
      if (Objects.equals(removedRole, ImConst.GROUP_ROLE_MANAGER)) {
        ChatErrors.require(Objects.equals(selfRole, ImConst.GROUP_ROLE_LEADER), "只有群主可以移除管理员");
      }
      ChatErrors.require(hasPower(selfRole), "没有权限移除成员");
      ChatErrors.require(removedRole != 4, "用户已经移除");
      Db.tx(() -> {
        Db.update("update im_group_member set is_del=1 where group_id=? and uid=?", groupId, removed);
        Db.update("delete from im_contact where room_id=? and uid=?", roomId, removed);
        return true;
      });
      Aop.get(OnlineService.class).leaveGroup(roomId, removed);
      List<Long> receivers = rooms().groupMemberUids(roomId, true);
      if (!receivers.contains(removed)) {
        receivers.add(removed);
      }
      Aop.get(GroupMemberChangeService.class).pushRemove(roomId, List.of(removed), receivers,
          GroupMemberChangeService.CHANGE_TYPE_REMOVE);
      long noticeId = SnowflakeIdUtils.id();
      noticeService.create(ImConst.ROOM_TYPE_GROUP, NoticeConst.EVENT_GROUP_MEMBER_DELETE, uid, removed, noticeId, removed,
          roomId, group.getStr("name"));
      for (Long manager : managerUids(groupId)) {
        noticeService.create(ImConst.ROOM_TYPE_GROUP, NoticeConst.EVENT_GROUP_MEMBER_DELETE, uid, manager, noticeId,
            removed, roomId, group.getStr("name"));
      }
    }
  }

  /**
   * 退出群聊；群主退出或 disband=true 时解散群聊
   */
  public void exit(boolean disband, long uid, Long roomId) {
    Kv group = requireGroup(roomId);
    long groupId = group.getLong("id");
    Kv room = rooms().getRoom(roomId);
    ChatErrors.require(!Values.isTrue(room.get("hot_flag")), "不允许退出全员群");
    Integer selfRole = role(uid, groupId);
    ChatErrors.require(selfRole != 4, "用户不在群里");
    boolean lord = Objects.equals(selfRole, ImConst.GROUP_ROLE_LEADER);
    List<Long> members = lord ? rooms().groupMemberUids(roomId, false) : rooms().groupMemberUids(roomId, true);
    if (disband || lord) {
      UserSummary user = Aop.get(UserSummaryService.class).get(uid);
      ChatMessageReq req = ChatMessageReq.of(roomId, MsgType.SYSTEM, Kv.by("content", user.getName() + "解散了群聊"));
      req.setSkip(true);
      Aop.get(MessageService.class).send(req, uid);
      Db.tx(() -> {
        Db.update("update im_room set is_del=1 where id=?", roomId);
        Db.update("update im_room_group set is_del=1 where id=?", groupId);
        Db.update("delete from im_contact where room_id=?", roomId);
        Db.update("update im_group_member set is_del=1 where group_id=?", groupId);
        Db.update("update im_message set is_del=1 where room_id=?", roomId);
        return true;
      });
      OnlineService onlineService = Aop.get(OnlineService.class);
      for (Long member : members) {
        onlineService.leaveGroup(roomId, member);
      }
      Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.ROOM_DISSOLUTION, String.valueOf(roomId)), members);
      return;
    }
    if (rooms().groupMemberUids(roomId, false).size() <= 3) {
      exit(true, uid, roomId);
      return;
    }
    Db.tx(() -> {
      Db.update("delete from im_contact where room_id=? and uid=?", roomId, uid);
      Db.update("update im_group_member set is_del=1 where group_id=? and uid=?", groupId, uid);
      return true;
    });
    Aop.get(OnlineService.class).leaveGroup(roomId, uid);
    Aop.get(GroupMemberChangeService.class).pushRemove(roomId, List.of(uid), members,
        GroupMemberChangeService.CHANGE_TYPE_QUIT);
  }

  public void disband(Long roomId) {
    Kv group = requireGroup(roomId);
    Long lord = Db.queryLong("select uid from im_group_member where group_id=? and role_id=? and is_del=0 limit 1",
        group.getLong("id"), ImConst.GROUP_ROLE_LEADER);
    ChatErrors.require(lord != null, "群主不存在");
    exit(true, lord, roomId);
  }

  public void setAdmin(long uid, Long roomId, List<Long> uidList, boolean add) {
    Kv group = requireGroup(roomId);
    long groupId = group.getLong("id");
    ChatErrors.require(Objects.equals(role(uid, groupId), ImConst.GROUP_ROLE_LEADER), "您没有权限操作");
    ChatErrors.require(uidList != null && !uidList.isEmpty(), "用户列表不能为空");
    for (Long target : uidList) {
      ChatErrors.require(role(target, groupId) != 4, "用户不在群里");
    }
    List<Long> managers = Db.query("select uid from im_group_member where group_id=? and role_id=? and is_del=0", groupId,
        ImConst.GROUP_ROLE_MANAGER);
    if (add) {
      Set<Long> all = new HashSet<>(managers);
      all.addAll(uidList);
      ChatErrors.require(all.size() <= MAX_MANAGE_COUNT, "管理员数量超过上限");
    }
    String sql = "update im_group_member set role_id=? where group_id=? and role_id<>1 and uid in ("
        + Values.placeholders(uidList.size()) + ")";
    List<Object> params = new ArrayList<>();
    params.add(add ? ImConst.GROUP_ROLE_MANAGER : ImConst.GROUP_ROLE_MEMBER);
    params.add(groupId);
    params.addAll(uidList);
    Db.update(sql, params.toArray());
    List<String> uids = new ArrayList<>();
    for (Long target : uidList) {
      uids.add(String.valueOf(target));
    }
    Kv data = Kv.by("roomId", String.valueOf(roomId)).set("uids", uids).set("status", add);
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.GROUP_SET_ADMIN, data), rooms().groupMemberUids(roomId, false));
    long noticeId = SnowflakeIdUtils.id();
    NoticeService noticeService = Aop.get(NoticeService.class);
    for (Long target : uidList) {
      if (add && managers.contains(target)) {
        continue;
      }
      noticeService.create(ImConst.ROOM_TYPE_GROUP, add ? NoticeConst.EVENT_GROUP_SET_ADMIN : NoticeConst.EVENT_GROUP_RECALL_ADMIN,
          uid, target, noticeId, target, roomId, "");
    }
  }

  /**
   * 修改群名称、头像、是否允许扫码进群，仅群主和管理员可操作
   */
  public boolean updateRoomInfo(long uid, Long roomId, String name, String avatar, Boolean allowScanEnter) {
    Kv group = rooms().getRoomGroupByRoomId(roomId);
    if (group == null || !hasPower(role(uid, group.getLong("id")))) {
      return false;
    }
    requireGroupNameLength(name);
    Db.update("update im_room_group set name=?, avatar=?, allow_scan_enter=? where id=?", name, avatar,
        Values.isTrue(allowScanEnter), group.getLong("id"));
    Kv data = Kv.by("roomId", String.valueOf(roomId)).set("name", name).set("avatar", avatar);
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.ROOM_INFO_CHANGE, data), rooms().groupMemberUids(roomId, true));
    return true;
  }

  /**
   * 默认群名为“创建者昵称的群组”，昵称过长时截断，保证不超过群名列长度
   */
  private String defaultGroupName(String ownerName) {
    String owner = ownerName == null ? "" : ownerName;
    int maxOwnerLength = MAX_GROUP_NAME_LENGTH - DEFAULT_GROUP_NAME_SUFFIX.length();
    if (owner.length() > maxOwnerLength) {
      owner = owner.substring(0, maxOwnerLength);
    }
    return owner + DEFAULT_GROUP_NAME_SUFFIX;
  }

  private void requireGroupNameLength(String name) {
    ChatErrors.require(name == null || name.length() <= MAX_GROUP_NAME_LENGTH, "群名称不能超过" + MAX_GROUP_NAME_LENGTH + "个字符");
  }

  public boolean updateMyRoomInfo(long uid, Long roomId, String myName, String remark) {
    Kv group = requireGroup(roomId);
    Kv member = Db.findFirstMap("select * from im_group_member where group_id=? and uid=? and is_del=0", group.getLong("id"), uid);
    ChatErrors.require(member != null, "您不是群成员");
    return updateMember(roomId, member, myName, remark);
  }

  public void updateMemberNickname(Long roomId, Long uid, String myName, String remark) {
    Kv group = requireGroup(roomId);
    Kv member = Db.findFirstMap("select * from im_group_member where group_id=? and uid=? and is_del=0", group.getLong("id"), uid);
    ChatErrors.require(member != null, "群成员不存在");
    updateMember(roomId, member, myName, remark);
  }

  private boolean updateMember(Long roomId, Kv member, String myName, String remark) {
    String old = member.getStr("my_name") == null ? "" : member.getStr("my_name");
    boolean changed = !old.equals(myName == null ? "" : myName);
    boolean ok = Db.update("update im_group_member set remark=?, my_name=? where id=?", remark, myName, member.getLong("id")) > 0;
    if (ok && changed) {
      Kv data = Kv.by("roomId", String.valueOf(roomId)).set("uid", String.valueOf(member.getLong("uid"))).set("myName", myName);
      Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.MY_ROOM_INFO_CHANGE, data), rooms().groupMemberUids(roomId, true));
    }
    return ok;
  }
}
