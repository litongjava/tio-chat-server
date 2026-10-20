package nexus.io.tio.chat.ws.call;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.im.chat.ChatMessageReq;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.service.im.RoomQueryService;
import nexus.io.tio.chat.service.im.chat.MessageService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.ws.PushService;

/**
 * 音视频通话：呼叫、应答、信令转发、超时与结束后生成通话记录消息
 */
@Slf4j
public class CallService {
  public static final int ACCEPT_TIMEOUT = -1;
  public static final int ACCEPT_REJECTED = 0;
  public static final int ACCEPT_ACCEPTED = 1;
  public static final int ACCEPT_HANGUP = 2;

  private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2);
  private static final Map<Long, ScheduledFuture<?>> TASKS = new ConcurrentHashMap<>();

  private CallRoomStore store() {
    return Aop.get(CallRoomStore.class);
  }

  private PushService push() {
    return Aop.get(PushService.class);
  }

  /**
   * 房间的全部成员（单聊两人、群聊全部成员）
   */
  private List<Long> roomUsers(long roomId) {
    return Aop.get(RoomQueryService.class).receiverUids(roomId);
  }

  private List<Long> notifyRoom(long roomId, long excludeUid, String type, Object data) {
    List<Long> uids = new ArrayList<>(roomUsers(roomId));
    uids.remove(excludeUid);
    if (!uids.isEmpty()) {
      push().push(WsBaseResp.of(type, data), uids);
    }
    return uids;
  }

  private List<Long> join(long uid, long roomId) {
    store().join(uid, roomId);
    refreshActivity(roomId);
    Kv data = Kv.by("uid", String.valueOf(uid)).set("roomId", String.valueOf(roomId)).set("name", "").set("avatar", "")
        .set("timestamp", System.currentTimeMillis());
    return notifyRoom(roomId, uid, WsRespType.JOIN_VIDEO, data);
  }

  public void request(long callerUid, Long roomId, Long targetUid, boolean video) {
    Kv room = Aop.get(RoomQueryService.class).getRoom(roomId);
    ChatErrors.require(room != null, "房间不存在");
    int roomType = room.getInt("type");
    store().open(roomId, callerUid, video, roomType);
    List<Long> pushIds = join(callerUid, roomId);
    if (roomType == ImConst.ROOM_TYPE_GROUP && video) {
      sendRecord(roomId, callerUid, callerUid, true, true, null, "ONGOING", true);
    }
    Kv data = Kv.by("roomId", String.valueOf(roomId)).set("callerUid", String.valueOf(callerUid))
        .set("targetUid", targetUid == null ? null : String.valueOf(targetUid)).set("isVideo", video);
    if (!pushIds.isEmpty()) {
      push().push(WsBaseResp.of(WsRespType.VIDEO_CALL_REQUEST, data), pushIds);
    }
    scheduleCallTimeout(callerUid, targetUid, roomId);
  }

  public void respond(long uid, Long callerUid, Long roomId, Integer accepted) {
    if (callerUid != null && (roomId == null || !Objects.equals(store().creator(roomId), callerUid) || store().isClosed(roomId))) {
      Long pending = pendingRoom(callerUid, uid);
      roomId = pending == null ? roomId : pending;
    }
    if (accepted == null || roomId == null) {
      return;
    }
    if (accepted == ACCEPT_ACCEPTED) {
      cancelTask(roomId);
      join(uid, roomId);
      push().push(WsBaseResp.of(WsRespType.CALL_ACCEPTED, Kv.by("acceptedBy", String.valueOf(uid)).set("roomId", String.valueOf(roomId))), callerUid);
      push().push(WsBaseResp.of(WsRespType.CALL_ACCEPTED, Kv.by("acceptedBy", String.valueOf(callerUid)).set("roomId", String.valueOf(roomId))), uid);
      store().setStartTime(roomId);
      return;
    }
    if (store().isClosed(roomId)) {
      return;
    }
    cancelTask(roomId);
    String type;
    String state;
    if (accepted == ACCEPT_REJECTED) {
      type = WsRespType.CALL_REJECTED;
      state = "REJECTED";
    } else if (accepted == ACCEPT_HANGUP) {
      boolean started = store().startTime(roomId) != null;
      type = started ? WsRespType.DROPPED : WsRespType.CANCEL;
      state = started ? "DROPPED" : "CANCEL";
    } else {
      type = WsRespType.TIMEOUT;
      state = "TIMEOUT";
    }
    push().push(WsBaseResp.of(type, Kv.by("rejectedBy", String.valueOf(uid))), callerUid);
    cleanRoom(roomId, uid, state);
  }

  public void forwardSignal(long senderUid, Long roomId, String signal, String signalType) {
    if (roomId == null || !store().isMember(senderUid, roomId)) {
      Long active = activeRoom(senderUid);
      roomId = active == null ? roomId : active;
    }
    if (roomId == null) {
      return;
    }
    Kv data = Kv.by("senderId", senderUid).set("roomId", roomId).set("signal", signal).set("signalType", signalType)
        .set("timestamp", System.currentTimeMillis());
    notifyRoom(roomId, senderUid, WsRespType.WEBRTC_SIGNAL, data);
  }

  /**
   * 桌面客户端的来电窗口取的是接听方当前打开的会话作为 roomId，接听方正在看其他会话时应答和信令带的是错误的房间，
   * 没有打开会话时不带房间：应答时改用呼叫方发起、且接听方所在的通话房间，信令改用发送方已加入的通话房间
   */
  private Long pendingRoom(long callerUid, long uid) {
    for (Long roomId : store().userRooms(callerUid)) {
      if (!store().isClosed(roomId) && roomUsers(roomId).contains(uid)) {
        return roomId;
      }
    }
    return null;
  }

  private Long activeRoom(long uid) {
    for (Long roomId : store().userRooms(uid)) {
      if (!store().isClosed(roomId)) {
        return roomId;
      }
    }
    return null;
  }

  public void forwardMediaControl(long senderUid, Kv control) {
    Long roomId = control.getLong("roomId");
    List<Long> members = store().members(roomId);
    members.remove(senderUid);
    if (!members.isEmpty()) {
      push().push(WsBaseResp.of(WsRespType.MEDIA_CONTROL, control), members);
    }
  }

  public void closeRoom(long operator, Long roomId) {
    if (store().isAdmin(operator, roomId)) {
      cleanRoom(roomId, operator, "MANAGER_CLOSE");
    }
  }

  public void kick(long operator, Long roomId, Long targetUid, String reason) {
    if (!store().isAdmin(operator, roomId)) {
      return;
    }
    leave(targetUid, roomId);
    Kv data = Kv.by("roomId", roomId).set("kickedUid", targetUid).set("operatorId", operator).set("reason", reason);
    push().push(WsBaseResp.of(WsRespType.USER_KICKED, data), targetUid);
    List<Long> members = store().members(roomId);
    members.remove(targetUid);
    if (!members.isEmpty()) {
      push().push(WsBaseResp.of(WsRespType.USER_KICKED, data), members);
    }
  }

  public void muteAll(long operator, Long roomId, boolean muted) {
    if (!store().isAdmin(operator, roomId)) {
      return;
    }
    store().set(roomId, "allMuted", muted ? "1" : "0");
    List<Long> members = store().members(roomId);
    if (!members.isEmpty()) {
      push().push(WsBaseResp.of(WsRespType.ALL_MUTED, Kv.by("roomId", roomId).set("muted", muted).set("operatorId", operator)), members);
    }
  }

  public void screenSharing(long uid, Long roomId, boolean sharing) {
    if (!store().isMember(uid, roomId)) {
      return;
    }
    store().set(roomId, "screenSharing", sharing ? String.valueOf(uid) : "");
    List<Long> members = store().members(roomId);
    members.remove(uid);
    if (!members.isEmpty()) {
      String type = sharing ? WsRespType.SCREEN_SHARING_STARTED : WsRespType.SCREEN_SHARING_STOPPED;
      push().push(WsBaseResp.of(type, Kv.by("roomId", roomId).set("userId", uid).set("sharing", sharing)), members);
    }
  }

  public void networkReport(long uid, Long roomId, double quality) {
    if (quality < 0.3) {
      Long creator = store().creator(roomId);
      if (creator != null) {
        Kv data = Kv.by("roomId", roomId).set("userId", uid).set("quality", quality).set("timestamp", System.currentTimeMillis());
        push().push(WsBaseResp.of(WsRespType.NETWORK_POOR, data), creator);
      }
    }
  }

  public void leave(long uid, Long roomId) {
    if (store().isClosed(roomId) || !store().isMember(uid, roomId)) {
      return;
    }
    store().leave(uid, roomId);
    notifyRoom(roomId, uid, WsRespType.LEAVE_VIDEO, Kv.by("uid", String.valueOf(uid)).set("roomId", String.valueOf(roomId))
        .set("timestamp", System.currentTimeMillis()));
    if (store().members(roomId).isEmpty()) {
      scheduleCleanup(roomId, 60);
    }
  }

  /**
   * 用户断线时离开所有通话房间
   */
  public void leaveAll(long uid) {
    for (Long roomId : store().userRooms(uid)) {
      leave(uid, roomId);
    }
  }

  public void refreshActivity(Long roomId) {
    scheduleCleanup(roomId, 300);
  }

  private void scheduleCleanup(long roomId, long seconds) {
    cancelTask(roomId);
    TASKS.put(roomId, SCHEDULER.schedule(() -> {
      if (!store().isClosed(roomId) && store().members(roomId).isEmpty()) {
        cleanRoom(roomId, null, "TIMEOUT");
      }
    }, seconds, TimeUnit.SECONDS));
  }

  private void scheduleCallTimeout(long caller, Long receiver, long roomId) {
    cancelTask(roomId);
    TASKS.put(roomId, SCHEDULER.schedule(() -> {
      if (store().isClosed(roomId)) {
        return;
      }
      push().push(WsBaseResp.of(WsRespType.TIMEOUT, Kv.by("targetUid", receiver == null ? null : String.valueOf(receiver))), caller);
      cleanRoom(roomId, null, "TIMEOUT");
    }, 30, TimeUnit.SECONDS));
  }

  private void cancelTask(Long roomId) {
    ScheduledFuture<?> task = TASKS.remove(roomId);
    if (task != null) {
      task.cancel(false);
    }
  }

  /**
   * 结束通话：生成通话记录消息并通知房间内成员
   */
  public synchronized void cleanRoom(long roomId, Long uid, String state) {
    if (store().isClosed(roomId)) {
      return;
    }
    cancelTask(roomId);
    Long creator = store().creator(roomId);
    Integer roomType = store().roomType(roomId);
    boolean group = roomType != null && roomType == ImConst.ROOM_TYPE_GROUP;
    try {
      sendRecord(roomId, creator, uid, group, store().isVideo(roomId), store().startTime(roomId), state, false);
    } catch (Exception e) {
      log.error("send call record failed, room={}", roomId, e);
    }
    store().markClosed(roomId);
    List<Long> members = store().members(roomId);
    store().clearMembers(roomId);
    if (!members.isEmpty()) {
      push().push(WsBaseResp.of(WsRespType.ROOM_CLOSED, Kv.by("roomId", String.valueOf(roomId)).set("reason", state)), members);
    }
  }

  private void sendRecord(long roomId, Long creator, Long uid, boolean group, boolean video, Long startTime, String state,
      boolean begin) {
    if (creator == null) {
      return;
    }
    long endTime = System.currentTimeMillis();
    long duration = startTime == null ? 0 : (long) Math.ceil((endTime - startTime) / 1000.0);
    Kv body = Kv.by("duration", duration).set("isGroup", group).set("creator", creator).set("state", state)
        .set("startTime", startTime).set("endTime", endTime);
    if (video) {
      body.set("begin", begin);
    }
    ChatMessageReq req = ChatMessageReq.of(roomId, video ? MsgType.VIDEO_CALL : MsgType.AUDIO_CALL, body);
    req.setSkip(true);
    Aop.get(MessageService.class).send(req, creator);
  }
}
