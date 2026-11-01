package nexus.io.tio.chat.service.im.group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.im.chat.ChatMessageReq;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.service.im.ContactService;
import nexus.io.tio.chat.service.im.RoomQueryService;
import nexus.io.tio.chat.service.im.RoomService;
import nexus.io.tio.chat.service.im.UserSummaryService;
import nexus.io.tio.chat.service.im.chat.MessageService;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.chat.ws.OnlineService;
import nexus.io.tio.chat.ws.PushService;

/**
 * 群成员加入、退出时的数据变更与推送
 */
public class GroupMemberChangeService {
  public static final int CHANGE_TYPE_ADD = 1;
  public static final int CHANGE_TYPE_REMOVE = 2;
  public static final int CHANGE_TYPE_QUIT = 3;

  /**
   * 把用户加入群：写成员、建会话、同步在线状态并推送 memberChange
   */
  public void join(long roomId, long groupId, Collection<Long> uids, long operator) {
    RoomService roomService = Aop.get(RoomService.class);
    ContactService contactService = Aop.get(ContactService.class);
    OnlineService onlineService = Aop.get(OnlineService.class);
    for (Long uid : uids) {
      roomService.createGroupMember(groupId, uid, ImConst.GROUP_ROLE_MEMBER, 1L);
      contactService.createContact(uid, roomId, 1L);
      onlineService.joinGroup(roomId, uid);
    }
    pushAdd(roomId, new ArrayList<>(uids), operator);
  }

  public void pushAdd(long roomId, List<Long> uids, long operator) {
    RoomQueryService roomQueryService = Aop.get(RoomQueryService.class);
    OnlineService onlineService = Aop.get(OnlineService.class);
    List<Long> members = roomQueryService.groupMemberUids(roomId, true);
    Set<Long> online = onlineService.groupOnlineMembers(roomId);
    Map<Long, UserSummary> users = Aop.get(UserSummaryService.class).getBatch(uids);
    List<Kv> userList = new ArrayList<>();
    String sql = "select id, uid, role_id, my_name from im_group_member where is_del=0 and uid in ("
        + Values.placeholders(uids.size()) + ") and group_id=(select id from im_room_group where room_id=?)";
    List<Object> params = new ArrayList<>(uids);
    params.add(roomId);
    for (Kv m : Db.findMaps(sql, params.toArray())) {
      long uid = m.getLong("uid");
      UserSummary user = users.get(uid);
      Kv item = Kv.by("id", String.valueOf(m.getLong("id"))).set("uid", String.valueOf(uid))
          .set("roleId", Values.toInt(m.get("role_id"))).set("myName", m.getStr("my_name"));
      if (user != null) {
        item.set("activeStatus", online.contains(uid) ? 1 : 2).set("lastOptTime", user.getLastOptTime())
            .set("name", user.getName()).set("avatar", user.getAvatar()).set("account", user.getAccount())
            .set("userStateId", String.valueOf(user.getUserStateId())).set("locPlace", user.getLocPlace());
      }
      userList.add(item);
    }
    Kv data = Kv.by("roomId", String.valueOf(roomId)).set("changeType", CHANGE_TYPE_ADD).set("userList", userList)
        .set("totalNum", members.size()).set("onlineNum", userList.size());
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.MEMBER_CHANGE, data), members);
  }

  public void pushRemove(long roomId, List<Long> removed, List<Long> receivers, int changeType) {
    RoomQueryService roomQueryService = Aop.get(RoomQueryService.class);
    int total = roomQueryService.groupMemberUids(roomId, false).size();
    long online = Aop.get(OnlineService.class).groupOnlineCount(roomId);
    List<Kv> userList = new ArrayList<>();
    for (Long uid : removed) {
      userList.add(Kv.by("uid", String.valueOf(uid)));
    }
    Kv data = Kv.by("roomId", String.valueOf(roomId)).set("changeType", changeType).set("userList", userList)
        .set("totalNum", total).set("onlineNum", (int) online);
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.MEMBER_CHANGE, data), receivers);
  }

  /**
   * 进群系统消息：申请进群、扫码进群或被邀请
   */
  public void sendJoinMessage(long roomId, long inviter, List<Long> members, boolean applyFor, Integer channel) {
    UserSummaryService summaryService = Aop.get(UserSummaryService.class);
    UserSummary inviterUser = summaryService.get(inviter);
    String inviterName = inviterUser == null ? "" : inviterUser.getName();
    String text;
    if (applyFor) {
      text = channel != null && channel == 2 ? inviterName + "扫码进群" : inviterName + "加入群聊";
    } else {
      List<String> names = new ArrayList<>();
      for (UserSummary user : summaryService.getBatch(members).values()) {
        names.add(user.getName());
      }
      text = inviterName + "邀请" + String.join("、", names) + "加入群聊";
    }
    ChatMessageReq req = ChatMessageReq.of(roomId, MsgType.SYSTEM, Kv.by("content", text));
    req.setSkip(true);
    Aop.get(MessageService.class).send(req, inviter);
  }
}
