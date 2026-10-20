package nexus.io.tio.chat.service.im.chat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.redis.Redis;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.consts.RedisKeys;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.im.chat.ChatMessageReq;
import nexus.io.tio.chat.model.im.chat.ChatMessageResp;
import nexus.io.tio.chat.model.im.chat.MessageRecord;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.service.im.RoomQueryService;
import nexus.io.tio.chat.service.im.chat.msg.MsgHandler;
import nexus.io.tio.chat.service.im.chat.msg.MsgHandlers;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.chat.ws.OnlineService;
import nexus.io.tio.chat.ws.PushService;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;
import nexus.io.tio.utils.thread.TioThreadUtils;

/**
 * 消息发送、持久化、推送与展示
 */
@Slf4j
public class MessageService {
  public static final int MARK_LIKE = 1;
  public static final int MARK_DISLIKE = 2;
  public static final int MARK_TYPE_MAX = 14;

  @SuppressWarnings("unchecked")
  public long send(ChatMessageReq req, long uid) {
    ChatErrors.require(req.getRoomId() != null, "房间号不能为空");
    ChatErrors.require(req.getMsgType() != null, "消息类型不能为空");
    if (!req.isSkip()) {
      checkRoomAccess(true, req.isTemp(), req.getRoomId(), uid);
    }
    MsgHandler handler = MsgHandlers.get(req.getMsgType());
    Map<String, Object> body;
    if (req.getBody() instanceof Map) {
      body = new LinkedHashMap<>((Map<String, Object>) req.getBody());
    } else if (req.getBody() == null) {
      body = new LinkedHashMap<>();
    } else if (req.getBody() instanceof String) {
      body = new LinkedHashMap<>();
      body.put("content", req.getBody());
    } else {
      body = JsonUtils.parseToMap(JsonUtils.toJson(req.getBody()), String.class, Object.class);
    }
    handler.check(body, req.getRoomId(), uid);

    MessageRecord message = new MessageRecord();
    message.setId(SnowflakeIdUtils.id());
    message.setRoomId(req.getRoomId());
    message.setFromUid(uid);
    message.setType(req.getMsgType());
    message.setStatus(MsgType.STATUS_NORMAL);
    message.setExtra(new LinkedHashMap<>());
    handler.fill(message, body);
    LocalDateTime now = LocalDateTime.now();
    message.setCreateTime(now);

    Row row = Row.by("id", message.getId()).set("room_id", message.getRoomId()).set("from_uid", uid)
        .set("content", message.getContent()).set("reply_msg_id", message.getReplyMsgId())
        .set("status", MsgType.STATUS_NORMAL).set("gap_count", message.getGapCount()).set("type", message.getType())
        .set("extra", JsonUtils.toJson(message.getExtra())).set("create_time", now).set("create_by", uid)
        .set("tenant_id", 1L);
    Db.save("im_message", row);

    if (req.isTemp() && !req.isPushMessage()) {
      Db.update("update im_user_friend set temp_msg_count=temp_msg_count+1 where room_id=? and uid=? and is_temp=1",
          req.getRoomId(), uid);
    }
    Db.update("update im_room set active_time=?, last_msg_id=? where id=?", now, message.getId(), req.getRoomId());
    TioThreadUtils.execute(() -> deliver(message, uid));
    return message.getId();
  }

  /**
   * 推送给房间内在线成员，并记录待确认消息
   */
  private void deliver(MessageRecord message, long senderUid) {
    try {
      RoomQueryService roomQueryService = Aop.get(RoomQueryService.class);
      PushService pushService = Aop.get(PushService.class);
      List<Long> members = roomQueryService.receiverUids(message.getRoomId());
      Set<Long> online = Aop.get(OnlineService.class).onlineOf(members);
      boolean call = Objects.equals(message.getType(), MsgType.VIDEO_CALL)
          || Objects.equals(message.getType(), MsgType.AUDIO_CALL);
      if (call) {
        // 通话记录：发起人看到自己的视角，其他成员看到发起人发出的视角
        pushService.push(WsBaseResp.of(WsRespType.RECEIVE_MESSAGE, getMsgResp(message, senderUid)), senderUid);
        online.remove(senderUid);
        for (Long member : online) {
          pushService.push(WsBaseResp.of(WsRespType.RECEIVE_MESSAGE, getMsgResp(message, member)), member);
        }
      } else {
        for (Long member : online) {
          pushService.push(WsBaseResp.of(WsRespType.RECEIVE_MESSAGE, getMsgResp(message, member)), member);
        }
      }
      for (Long member : online) {
        Redis.call(jedis -> jedis.sadd(RedisKeys.PASSAGE_MSG + member, String.valueOf(message.getId())));
      }
    } catch (Exception e) {
      log.error("deliver message failed, id={}", message.getId(), e);
    }
  }

  public void checkRoomAccess(boolean isSend, boolean isTemp, long roomId, long uid) {
    RoomQueryService roomQueryService = Aop.get(RoomQueryService.class);
    Kv room = roomQueryService.getRoom(roomId);
    ChatErrors.require(room != null, "房间不存在!");
    if (roomQueryService.isGroup(room)) {
      Kv member = roomQueryService.getMember(roomId, uid);
      ChatErrors.require(member != null, "您已经被移除该群");
      if (Values.isTrue(member.get("de_friend"))) {
        throw ChatErrors.of(isSend ? "你已屏蔽群聊，无法发送消息" : "您已经屏蔽群聊!");
      }
      return;
    }
    Kv friend = roomQueryService.getRoomFriendByRoomId(roomId);
    ChatErrors.require(friend != null, "房间不存在!");
    boolean u1 = Objects.equals(friend.getLong("uid1"), uid);
    boolean u2 = Objects.equals(friend.getLong("uid2"), uid);
    if (isTemp) {
      Kv userFriend = Db.findFirstMap("select * from im_user_friend where room_id=? and uid=? and is_del=0", roomId, uid);
      if (userFriend == null || !Values.isTrue(userFriend.get("is_temp"))) {
        throw ChatErrors.of("当前会话不存在或不是临时会话");
      }
      int count = Values.toInt(userFriend.get("temp_msg_count")) == null ? 0 : Values.toInt(userFriend.get("temp_msg_count"));
      if (u1 && !Values.isTrue(userFriend.get("temp_status")) && count >= 1) {
        throw ChatErrors.of("对方未回复前只能发送一条打招呼信息");
      }
      if (count >= 5) {
        throw ChatErrors.of("临时会话最多只能发送5条消息");
      }
    }
    boolean de1 = Values.isTrue(friend.get("de_friend1"));
    boolean de2 = Values.isTrue(friend.get("de_friend2"));
    if (u1) {
      if (de1) {
        throw ChatErrors.of("你已屏蔽对方，无法发送消息");
      } else if (de2) {
        throw ChatErrors.of("对方已屏蔽你，无法发送消息");
      }
    } else if (u2) {
      if (de2) {
        throw ChatErrors.of("你已屏蔽对方，无法发送消息");
      } else if (de1) {
        throw ChatErrors.of("对方已屏蔽你，无法发送消息");
      }
    }
    ChatErrors.require(u1 || u2, "消息已发送, 但对方拒收了");
  }

  public MessageRecord getById(long msgId) {
    return MessageRecord.from(Db.findFirstMap("select * from im_message where id=?", msgId));
  }

  public ChatMessageResp getMsgResp(long msgId, Long receiveUid) {
    MessageRecord message = getById(msgId);
    return message == null ? null : getMsgResp(message, receiveUid);
  }

  public ChatMessageResp getMsgResp(MessageRecord message, Long receiveUid) {
    List<ChatMessageResp> list = buildResp(List.of(message), receiveUid);
    return list.isEmpty() ? null : list.get(0);
  }

  public List<ChatMessageResp> buildResp(List<MessageRecord> messages, Long receiveUid) {
    List<ChatMessageResp> result = new ArrayList<>();
    if (messages.isEmpty()) {
      return result;
    }
    List<Long> ids = new ArrayList<>();
    for (MessageRecord m : messages) {
      ids.add(m.getId());
    }
    Map<Long, List<Kv>> marks = new HashMap<>();
    String sql = "select msg_id, uid, type from im_message_mark where status=0 and msg_id in ("
        + Values.placeholders(ids.size()) + ")";
    for (Kv mark : Db.findMaps(sql, ids.toArray())) {
      marks.computeIfAbsent(mark.getLong("msg_id"), k -> new ArrayList<>()).add(mark);
    }
    for (MessageRecord m : messages) {
      ChatMessageResp resp = new ChatMessageResp();
      resp.setFromUser(new ChatMessageResp.UserInfo(String.valueOf(m.getFromUid())));
      ChatMessageResp.Message vo = new ChatMessageResp.Message();
      vo.setId(String.valueOf(m.getId()));
      vo.setRoomId(String.valueOf(m.getRoomId()));
      vo.setSendTime(m.getCreateTime());
      vo.setType(m.getType());
      MsgHandler handler = MsgHandlers.find(m.getType());
      if (handler != null) {
        vo.setBody(handler.show(m, receiveUid));
      }
      vo.setMessageMarks(buildMarks(marks.get(m.getId()), receiveUid));
      resp.setMessage(vo);
      result.add(resp);
    }
    result.sort(Comparator.comparing(r -> r.getMessage().getSendTime(), Comparator.nullsFirst(Comparator.naturalOrder())));
    return result;
  }

  private Map<Integer, ChatMessageResp.MarkItem> buildMarks(Collection<Kv> marks, Long receiveUid) {
    Map<Integer, ChatMessageResp.MarkItem> stats = new HashMap<>();
    if (marks == null || marks.isEmpty()) {
      return stats;
    }
    for (int type = 1; type <= MARK_TYPE_MAX; type++) {
      int count = 0;
      boolean marked = false;
      for (Kv mark : marks) {
        if (Objects.equals(Values.toInt(mark.get("type")), type)) {
          count++;
          if (Objects.equals(mark.getLong("uid"), receiveUid)) {
            marked = true;
          }
        }
      }
      stats.put(type, new ChatMessageResp.MarkItem(count, marked));
    }
    return stats;
  }
}
