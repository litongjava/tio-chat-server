package nexus.io.tio.chat.service.im.chat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.model.common.PageBaseResp;
import nexus.io.tio.chat.model.im.chat.ChatMessageResp;
import nexus.io.tio.chat.model.im.chat.MessageRecord;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.service.im.UserSummaryService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;

/**
 * 聊天记录检索与文件消息列表（客户端原本在本地 SQLite 中查询，浏览器等无本地库的客户端改为服务端查询）
 */
public class MessageSearchService {

  /** 文件管理默认只列出文件与视频消息，与客户端本地实现一致 */
  public static final List<Integer> FILE_TYPES = List.of(MsgType.FILE, MsgType.VIDEO);

  /**
   * 单个房间内的聊天记录：按消息类型、关键词、时间范围筛选，页码分页
   */
  public PageBaseResp<ChatMessageResp> search(long uid, long roomId, List<Integer> types, String keyword, Long startTime,
      Long endTime, boolean asc, int pageNo, int pageSize) {
    Aop.get(MessageService.class).checkRoomAccess(false, false, roomId, uid);
    ChatService chatService = Aop.get(ChatService.class);
    List<Object> params = new ArrayList<>();
    StringBuilder where = new StringBuilder(" from im_message where room_id=? and status=0 and is_del=0");
    params.add(roomId);
    // 被移出群聊的成员只能检索移出前的消息
    Long upperBound = chatService.lastVisibleMsgId(roomId, uid);
    if (upperBound != null) {
      where.append(" and id<=?");
      params.add(upperBound);
    }
    appendTypes(where, params, types);
    appendKeyword(where, params, keyword);
    if (startTime != null) {
      where.append(" and create_time>=?");
      params.add(toTime(startTime));
    }
    if (endTime != null) {
      where.append(" and create_time<=?");
      params.add(toTime(endTime));
    }
    long total = Db.queryLong("select count(*)" + where, params.toArray());
    if (total == 0) {
      return PageBaseResp.init(pageNo, pageSize, 0, new ArrayList<>());
    }
    String order = asc ? " order by create_time asc, id asc" : " order by create_time desc, id desc";
    params.add(pageSize);
    params.add((pageNo - 1) * pageSize);
    List<MessageRecord> messages = new ArrayList<>();
    for (Kv row : Db.findMaps("select *" + where + order + " limit ? offset ?", params.toArray())) {
      messages.add(MessageRecord.from(row));
    }
    List<ChatMessageResp> list = chatService.filterBlack(Aop.get(MessageService.class).buildResp(messages, uid));
    if (!asc) {
      Collections.reverse(list);
    }
    return PageBaseResp.init(pageNo, pageSize, total, list);
  }

  /**
   * 当前用户可访问房间内的文件消息，附带发送人昵称与头像。
   * senderUid 不为空时只看与该用户有过消息往来的房间（与客户端本地实现一致）
   */
  public PageBaseResp<Kv> files(long uid, Long roomId, Long senderUid, String keyword, List<Integer> types, int pageNo,
      int pageSize) {
    ChatService chatService = Aop.get(ChatService.class);
    List<Long> roomIds = chatService.accessibleRoomIds(uid);
    if (roomId != null) {
      ChatErrors.require(roomIds.contains(roomId), "房间不存在!");
      roomIds = List.of(roomId);
    }
    if (roomIds.isEmpty()) {
      return PageBaseResp.init(pageNo, pageSize, 0, new ArrayList<>());
    }
    List<Object> params = new ArrayList<>(roomIds);
    StringBuilder where = new StringBuilder(" from im_message where status=0 and is_del=0 and room_id in (")
        .append(Values.placeholders(roomIds.size())).append(")");
    if (senderUid != null) {
      where.append(" and room_id in (select distinct room_id from im_message where from_uid=? and is_del=0)");
      params.add(senderUid);
    }
    appendTypes(where, params, types == null || types.isEmpty() ? FILE_TYPES : types);
    appendKeyword(where, params, keyword);
    long total = Db.queryLong("select count(*)" + where, params.toArray());
    if (total == 0) {
      return PageBaseResp.init(pageNo, pageSize, 0, new ArrayList<>());
    }
    params.add(pageSize);
    params.add((pageNo - 1) * pageSize);
    List<MessageRecord> messages = new ArrayList<>();
    List<Long> senders = new ArrayList<>();
    for (Kv row : Db.findMaps("select *" + where + " order by create_time desc, id desc limit ? offset ?", params.toArray())) {
      MessageRecord message = MessageRecord.from(row);
      messages.add(message);
      senders.add(message.getFromUid());
    }
    List<ChatMessageResp> resps = chatService.filterBlack(Aop.get(MessageService.class).buildResp(messages, uid));
    Collections.reverse(resps);
    Map<Long, UserSummary> users = Aop.get(UserSummaryService.class).getBatch(senders);
    List<Kv> list = new ArrayList<>();
    for (ChatMessageResp resp : resps) {
      UserSummary user = users.get(Long.valueOf(resp.getFromUser().getUid()));
      Kv sender = Kv.by("uid", resp.getFromUser().getUid()).set("name", user == null ? null : user.getName())
          .set("avatar", user == null ? null : user.getAvatar());
      list.add(Kv.by("fromUser", sender).set("message", resp.getMessage()));
    }
    return PageBaseResp.init(pageNo, pageSize, total, list);
  }

  private void appendTypes(StringBuilder where, List<Object> params, List<Integer> types) {
    if (types == null || types.isEmpty()) {
      return;
    }
    where.append(" and type in (").append(Values.placeholders(types.size())).append(")");
    params.addAll(types);
  }

  /**
   * 关键词匹配文本内容、文件名与链接；已撤回消息的原文不参与匹配
   */
  private void appendKeyword(StringBuilder where, List<Object> params, String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return;
    }
    String pattern = "%" + escapeLike(keyword.trim().toLowerCase()) + "%";
    where.append(" ").append("""
        and type<>? and (lower(content) like ?
        or lower(cast(json_extract(extra, '$.*.fileName') as char)) like ?
        or lower(cast(json_extract(extra, '$.*.url') as char)) like ?)""");
    params.add(MsgType.RECALL);
    params.add(pattern);
    params.add(pattern);
    params.add(pattern);
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static LocalDateTime toTime(long millis) {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
  }
}
