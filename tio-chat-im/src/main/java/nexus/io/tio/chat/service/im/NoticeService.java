package nexus.io.tio.chat.service.im;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.consts.NoticeConst;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.common.PageBaseResp;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.chat.ws.PushService;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 好友与群相关的通知
 */
public class NoticeService {

  public long create(int roomType, int eventType, long senderId, long receiverId, long applyId, long operateId,
      long roomId, String content) {
    long id = SnowflakeIdUtils.id();
    Row row = Row.by("id", id).set("event_type", eventType).set("type", roomType).set("sender_id", senderId)
        .set("receiver_id", receiverId).set("room_id", roomId).set("apply_id", applyId).set("operate_id", operateId)
        .set("content", content == null ? "" : content).set("status", NoticeConst.STATUS_UNTREATED)
        .set("is_read", NoticeConst.UNREAD).set("create_by", senderId).set("tenant_id", 1L);
    DbRows.save("im_notice", row);
    push(Db.findFirstMap("select * from im_notice where id=?", id));
    return id;
  }

  public Kv getByApplyId(long receiverId, long applyId) {
    return Db.findFirstMap("select * from im_notice where receiver_id=? and apply_id=? and is_del=0 limit 1", receiverId,
        applyId);
  }

  /**
   * 更新某个申请的全部通知状态，并通知接收人
   */
  public void updateStatus(long applyId, int status, boolean pushAll, long receiverId) {
    Db.update("update im_notice set status=? where apply_id=?", status, applyId);
    if (pushAll) {
      for (Kv notice : Db.findMaps("select * from im_notice where apply_id=? and is_del=0", applyId)) {
        push(notice);
      }
    } else {
      push(getByApplyId(receiverId, applyId));
    }
  }

  private void push(Kv notice) {
    if (notice == null) {
      return;
    }
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.NOTIFY_EVENT, toVO(notice)), notice.getLong("receiver_id"));
  }

  public Kv toVO(Kv notice) {
    UserSummaryService summaryService = Aop.get(UserSummaryService.class);
    Map<Long, UserSummary> users = summaryService.getBatch(List.of(notice.getLong("sender_id"), notice.getLong("receiver_id")));
    UserSummary sender = users.get(notice.getLong("sender_id"));
    UserSummary receiver = users.get(notice.getLong("receiver_id"));
    return Kv.by("id", notice.getLong("id")).set("eventType", Values.toInt(notice.get("event_type")))
        .set("type", Values.toInt(notice.get("type"))).set("applyId", notice.getLong("apply_id"))
        .set("operateId", notice.getLong("operate_id")).set("roomId", notice.getLong("room_id"))
        .set("content", notice.getStr("content")).set("status", Values.toInt(notice.get("status")))
        .set("createTime", Values.time(notice.get("create_time"))).set("read", Values.toInt(notice.get("is_read")))
        .set("senderName", sender == null ? null : sender.getName())
        .set("senderAvatar", sender == null ? null : sender.getAvatar())
        .set("receiverName", receiver == null ? null : receiver.getName())
        .set("receiverAvatar", receiver == null ? null : receiver.getAvatar())
        .set("senderId", notice.getLong("sender_id")).set("receiverId", notice.getLong("receiver_id"));
  }

  /**
   * 通知分页，click=true 时把本页对应类型的通知标记为已读
   */
  public PageBaseResp<Kv> page(long uid, int pageNo, int pageSize, boolean click, String applyType) {
    long total = Db.queryLong("select count(*) from im_notice where receiver_id=? and is_del=0", uid);
    List<Kv> rows = Db.findMaps(
        "select * from im_notice where receiver_id=? and is_del=0 order by create_time desc limit ?, ?", uid,
        (pageNo - 1) * pageSize, pageSize);
    if (click && !rows.isEmpty()) {
      int type = "friend".equals(applyType) ? ImConst.ROOM_TYPE_FRIEND : ImConst.ROOM_TYPE_GROUP;
      List<Object> params = new ArrayList<>();
      params.add(NoticeConst.READ);
      params.add(uid);
      params.add(type);
      for (Kv row : rows) {
        params.add(row.getLong("id"));
      }
      String sql = "update im_notice set is_read=? where receiver_id=? and type=? and is_read=1 and id in ("
          + Values.placeholders(rows.size()) + ")";
      Db.update(sql, params.toArray());
    }
    List<Kv> list = new ArrayList<>();
    for (Kv row : rows) {
      list.add(toVO(row));
    }
    return PageBaseResp.init(pageNo, pageSize, total, list);
  }

  public void markAsRead(Long noticeId) {
    Db.update("update im_notice set is_read=? where id=?", NoticeConst.READ, noticeId);
  }

  /**
   * 未读通知数：unReadCount4Friend / unReadCount4Group
   */
  public Kv unread(long uid, long receiverId) {
    Kv result = Kv.by("uid", uid).set("unReadCount4Friend", null).set("unReadCount4Group", null);
    String sql = "select type, count(*) cnt from im_notice where receiver_id=? and is_read=? and is_del=0 group by type";
    for (Kv row : Db.findMaps(sql, receiverId, NoticeConst.UNREAD)) {
      if (Values.toInt(row.get("type")) == ImConst.ROOM_TYPE_FRIEND) {
        result.set("unReadCount4Friend", Values.toInt(row.get("cnt")));
      } else {
        result.set("unReadCount4Group", Values.toInt(row.get("cnt")));
      }
    }
    return result;
  }
}
