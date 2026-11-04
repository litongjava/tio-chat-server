package nexus.io.tio.chat.service.base.msg;

import java.time.LocalDateTime;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Row;
import nexus.io.tio.chat.utils.DbRows;

/**
 * 站内通知：给每个接收人写一条 extend_notice
 */
public class NoticeMsgStrategy implements MsgStrategy {
  private static final String REMIND_MODE_NOTICE = "02";

  @Override
  public MsgResult exec(MsgParam param) throws Exception {
    Kv msg = param.getMsg();
    Kv template = param.getTemplate();
    Map<String, Object> values = MsgTemplates.values(param);
    String title = MsgTemplates.render(template.getStr("title"), values);
    String content = MsgTemplates.render(template.getStr("content"), values);
    for (Kv recipient : param.getRecipients()) {
      DbRows.save("extend_notice", Row.by("msg_id", msg.getLong("id")).set("biz_id", msg.get("biz_id"))
          .set("biz_type", msg.get("biz_type")).set("recipient_id", Long.valueOf(recipient.getStr("recipient")))
          .set("remind_mode", REMIND_MODE_NOTICE).set("title", title).set("content", content)
          .set("author", msg.get("author")).set("url", template.get("url")).set("target_", template.get("target_"))
          .set("auto_read", template.get("auto_read")).set("is_read", 0).set("is_handle", 0)
          .set("created_time", LocalDateTime.now()));
    }
    return new MsgResult(true, title, content, "ok");
  }
}
