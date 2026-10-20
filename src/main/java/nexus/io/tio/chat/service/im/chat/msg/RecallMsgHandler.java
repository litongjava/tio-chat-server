package nexus.io.tio.chat.service.im.chat.msg;

import java.util.Map;
import java.util.Objects;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.model.im.chat.MessageRecord;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.service.im.UserSummaryService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;

/**
 * 撤回消息，展示内容根据查看者身份变化
 */
public class RecallMsgHandler extends AbstractMsgHandler {

  @Override
  public int type() {
    return MsgType.RECALL;
  }

  @Override
  public void fill(MessageRecord message, Map<String, Object> body) {
    throw ChatErrors.of("撤回消息不能直接发送");
  }

  @Override
  public Object show(MessageRecord message, Long receiveUid) {
    Map<String, Object> recall = message.extraMap("recall");
    Long recallUid = recall == null ? message.getFromUid() : Values.toLong(recall.get("recallUid"));
    Long senderUid = message.getFromUid();
    String roleName = "";
    Integer roomType = Db.queryInt("select type from im_room where id=?", message.getRoomId());
    if (Objects.equals(roomType, ImConst.ROOM_TYPE_GROUP)) {
      String sql = """
          select m.role_id from im_group_member m join im_room_group g on g.id=m.group_id
          where g.room_id=? and m.uid=? and m.is_del=0 limit 1
          """;
      Integer roleId = Db.queryInt(sql, message.getRoomId(), recallUid);
      if (Objects.equals(roleId, ImConst.GROUP_ROLE_LEADER)) {
        roleName = "群主";
      } else if (Objects.equals(roleId, ImConst.GROUP_ROLE_MANAGER)) {
        roleName = "管理";
      }
    }
    UserSummaryService summaryService = Aop.get(UserSummaryService.class);
    UserSummary recaller = summaryService.get(recallUid);
    UserSummary sender = summaryService.get(senderUid);
    String recallerName = recaller == null ? "" : recaller.getName();
    String senderName = sender == null ? "" : sender.getName();
    String text;
    if (Objects.equals(recallUid, receiveUid)) {
      text = Objects.equals(recallUid, senderUid) ? "你撤回了一条消息" : "你撤回了成员" + senderName + "的一条消息";
    } else if (Objects.equals(senderUid, receiveUid)) {
      text = roleName + recallerName + "撤回了你的一条消息";
    } else {
      text = roleName + recallerName + "撤回了一条消息";
    }
    return Kv.by("content", text);
  }

  @Override
  public Object showReply(MessageRecord message) {
    return "原消息已被撤回";
  }

  @Override
  public String showContact(MessageRecord message) {
    return "撤回了一条消息";
  }
}
