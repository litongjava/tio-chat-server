package nexus.io.tio.chat.service.base.msg;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson2.JSON;
import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.hook.HookCan;
import nexus.io.redis.Redis;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.TimeParams;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;
import nexus.io.tio.utils.thread.TioThreadUtils;

/**
 * 消息中心：发布站内信、按模板发送、定时发送。发送时按 模板 → 接口 → 策略 选择渠道并记录接口日志。
 * 到期的定时消息由轮询任务发送，多实例下用 Redis 锁保证只有一个实例执行。
 */
@Slf4j
public class MsgSendService {
  public static final String STATUS_DRAFT = "DRAFT";
  public static final String STATUS_WAITING = "WAITING";
  public static final String STATUS_SUCCESS = "SUCCESS";
  public static final String STATUS_FAIL = "FAIL";
  private static final String CHANNEL_APP = "APP";
  private static final String CHANNEL_SERVICE = "SERVICE";
  private static final String TYPE_NOTICE = "04";
  private static final String EXEC_MODE_IMPL = "01";
  private static final String LOGGING_SUCCESS = "02";
  private static final String LOGGING_FAIL = "03";
  private static final int MIN_DELAY_MINUTES = 4;
  private static final String SCHEDULE_LOCK = "chat:msg:schedule";
  private static final int SCHEDULE_LOCK_SECONDS = 25;
  private static final Map<String, MsgStrategy> STRATEGIES = new HashMap<>();

  static {
    STRATEGIES.put("noticeMsgStrategyImpl", new NoticeMsgStrategy());
    STRATEGIES.put("tencentMailMsgStrategyImpl", new MailMsgStrategy());
    STRATEGIES.put("aliSmsMsgStrategyImpl", new AliSmsMsgStrategy());
    STRATEGIES.put("baiduSmsMsgStrategyImpl", new BaiduSmsMsgStrategy());
    STRATEGIES.put("clSmsMsgStrategyImpl", new ClSmsMsgStrategy());
    STRATEGIES.put("tencentSmsMsgStrategyImpl", new TencentSmsMsgStrategy());
  }

  /** 按模板发送，没有 sendTime 时立即在后台执行 */
  public boolean sendByTemplate(Map<String, Object> data) {
    String code = (String) data.get("code");
    ChatErrors.require(code != null && !code.isEmpty(), "请选择消息模板");
    Kv template = Db.findFirstMap("select * from def_msg_template where code=? and is_del=0 limit 1", code);
    ChatErrors.require(template != null, "请选择正确的消息模板");
    List<Kv> recipients = templateRecipients(data.get("recipientList"));
    ChatErrors.require(!recipients.isEmpty(), "请填写消息接收人");
    LocalDateTime sendTime = sendTime(data);

    long id = SnowflakeIdUtils.id();
    String param = data.get("paramList") == null ? null : JSON.toJSONString(data.get("paramList"));
    DbRows.save("extend_msg", msgRow(id, data, sendTime).set("template_code", code).set("type", template.getStr("type"))
        .set("channel", CHANNEL_SERVICE).set("status", STATUS_WAITING).set("param", param)
        .set("title", data.get("title")).set("content", data.get("content"))
        .set("remind_mode", template.getStr("remind_mode")));
    saveRecipients(id, recipients);
    if (sendTime == null) {
      TioThreadUtils.submit(() -> exec(id));
    }
    return true;
  }

  /** 发布站内信：草稿只保存；没有 sendTime 时立即生成通知 */
  public boolean publish(Map<String, Object> data) {
    boolean draft = Values.isTrue(data.get("draft"));
    String title = (String) data.get("title");
    String content = (String) data.get("content");
    ChatErrors.require(title != null && !title.isEmpty(), "请填写标题");
    ChatErrors.require(content != null && !content.isEmpty(), "请填写内容");
    List<Kv> recipients = templateRecipients(data.get("recipientList"));
    ChatErrors.require(!recipients.isEmpty(), "请填写消息接收人");
    LocalDateTime sendTime = sendTime(data);
    String status = draft ? STATUS_DRAFT : STATUS_WAITING;
    String remindMode = data.get("remindMode") == null ? "02" : data.get("remindMode").toString();

    Long id = Values.toLong(data.get("id"));
    long msgId = id == null ? SnowflakeIdUtils.id() : id;
    return Db.tx(() -> {
      if (id == null) {
        DbRows.save("extend_msg", msgRow(msgId, data, sendTime).set("type", TYPE_NOTICE).set("channel", CHANNEL_APP)
            .set("status", status).set("title", title).set("content", content).set("remind_mode", remindMode));
      } else {
        Db.update("update extend_msg set title=?, content=?, remind_mode=?, status=?, send_time=? where id=?", title,
            content, remindMode, status, sendTime, msgId);
        Db.update("update extend_msg_recipient set is_del=1 where msg_id=?", msgId);
      }
      saveRecipients(msgId, recipients);
      if (!draft && sendTime == null) {
        publishNotice(msgId);
      }
      return true;
    });
  }

  /** 把站内信发给保存的接收人 */
  public void publishNotice(long msgId) {
    Kv msg = Db.findFirstMap("select * from extend_msg where id=? and is_del=0", msgId);
    ChatErrors.require(msg != null, "消息不存在");
    List<Kv> recipients = recipients(msgId);
    ChatErrors.require(!recipients.isEmpty(), "消息接收人为空");
    for (Kv recipient : recipients) {
      DbRows.save("extend_notice", Row.by("msg_id", msgId).set("biz_id", msg.get("biz_id")).set("biz_type", msg.get("biz_type"))
          .set("recipient_id", Long.valueOf(recipient.getStr("recipient"))).set("remind_mode", msg.getStr("remind_mode"))
          .set("title", msg.getStr("title")).set("content", msg.getStr("content")).set("author", msg.get("author"))
          .set("is_read", 0).set("is_handle", 0).set("auto_read", 1).set("created_time", LocalDateTime.now()));
    }
    Db.update("update extend_msg set status=? where id=?", STATUS_SUCCESS, msgId);
  }

  /** 通过模板配置的接口发送，结果写入消息状态和接口日志 */
  public boolean exec(long msgId) {
    Kv msg = Db.findFirstMap("select * from extend_msg where id=? and is_del=0", msgId);
    if (msg == null) {
      return false;
    }
    Kv template = Db.findFirstMap("select * from def_msg_template where code=? and is_del=0 limit 1", msg.getStr("template_code"));
    Kv api = template == null ? null : Db.findFirstMap("select * from def_interface where id=? and is_del=0", template.getLong("interface_id"));
    if (api == null) {
      log.error("msg {}: template {} has no interface", msgId, msg.getStr("template_code"));
      Db.update("update extend_msg set status=? where id=?", STATUS_FAIL, msgId);
      return false;
    }
    long logId = interfaceLog(api);
    Row logging = Row.by("id", SnowflakeIdUtils.id()).set("log_id", logId).set("biz_id", msg.get("biz_id"))
        .set("exec_time", LocalDateTime.now()).set("params", msg.get("param"));
    boolean success = false;
    try {
      MsgParam param = new MsgParam(msg, template, recipients(msgId), properties(api.getLong("id")));
      MsgResult result = strategy(api).exec(param);
      success = result.isSuccess();
      logging.set("result", result.getRaw());
      if (result.getTitle() != null || result.getContent() != null) {
        Db.update("update extend_msg set title=?, content=? where id=?", result.getTitle(), result.getContent(), msgId);
      }
    } catch (Exception e) {
      log.error("send msg {} through {} failed", msgId, api.getStr("code"), e);
      logging.set("error_msg", e.getMessage());
    }
    logging.set("status", success ? LOGGING_SUCCESS : LOGGING_FAIL);
    DbRows.save("extend_interface_logging", logging);
    Db.update("update extend_interface_log set " + (success ? "success_count=success_count+1" : "fail_count=fail_count+1")
        + ", last_exec_time=? where id=?", LocalDateTime.now(), logId);
    Db.update("update extend_msg set status=? where id=?", success ? STATUS_SUCCESS : STATUS_FAIL, msgId);
    return success;
  }

  /** 启动定时消息轮询，间隔 chat.msg.schedule.interval-seconds，默认 30 秒 */
  public void startScheduler() {
    long interval = EnvUtils.getLong("chat.msg.schedule.interval-seconds", 30L);
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "chat-msg-schedule");
      thread.setDaemon(true);
      return thread;
    });
    scheduler.scheduleWithFixedDelay(this::sendDue, interval, interval, TimeUnit.SECONDS);
    HookCan.me().addDestroyMethod(scheduler::shutdownNow);
  }

  void sendDue() {
    String lockId = null;
    try {
      lockId = Redis.use().lock(SCHEDULE_LOCK, SCHEDULE_LOCK_SECONDS, 0);
      if (lockId == null) {
        return;
      }
      String sql = "select id, template_code from extend_msg where status=? and send_time is not null and send_time<=? and is_del=0 limit 100";
      for (Kv due : Db.findMaps(sql, STATUS_WAITING, LocalDateTime.now())) {
        sendOne(due);
      }
    } catch (Exception e) {
      log.error("send due messages failed", e);
    } finally {
      if (lockId != null) {
        Redis.use().unlock(SCHEDULE_LOCK, lockId);
      }
    }
  }

  private void sendOne(Kv due) {
    long id = due.getLong("id");
    try {
      if (due.getStr("template_code") == null) {
        publishNotice(id);
      } else {
        exec(id);
      }
    } catch (Exception e) {
      log.error("send scheduled msg {} failed", id, e);
      Db.update("update extend_msg set status=? where id=?", STATUS_FAIL, id);
    }
  }

  private MsgStrategy strategy(Kv api) throws Exception {
    if (!EXEC_MODE_IMPL.equals(api.getStr("exec_mode"))) {
      return MsgGroovy.strategy(api.getStr("script"));
    }
    MsgStrategy strategy = STRATEGIES.get(api.getStr("impl_class"));
    if (strategy == null) {
      throw new IllegalStateException("实现类[" + api.getStr("impl_class") + "]不存在");
    }
    return strategy;
  }

  private long interfaceLog(Kv api) {
    Long id = Db.queryLong("select id from extend_interface_log where interface_id=? and is_del=0 limit 1", api.getLong("id"));
    if (id != null) {
      return id;
    }
    long newId = SnowflakeIdUtils.id();
    DbRows.save("extend_interface_log", Row.by("id", newId).set("interface_id", api.getLong("id")).set("name", api.getStr("name"))
        .set("success_count", 0).set("fail_count", 0));
    return newId;
  }

  private Map<String, String> properties(long interfaceId) {
    Map<String, String> result = new LinkedHashMap<>();
    for (Kv row : Db.findMaps("select key_, value from def_interface_property where interface_id=? and is_del=0", interfaceId)) {
      result.put(row.getStr("key_"), row.getStr("value"));
    }
    return result;
  }

  private List<Kv> recipients(long msgId) {
    return Db.findMaps("select recipient, ext from extend_msg_recipient where msg_id=? and is_del=0", msgId);
  }

  private void saveRecipients(long msgId, List<Kv> recipients) {
    for (Kv recipient : recipients) {
      DbRows.save("extend_msg_recipient", Row.by("id", SnowflakeIdUtils.id()).set("msg_id", msgId)
          .set("recipient", recipient.getStr("recipient")).set("ext", recipient.get("ext")).set("create_by", operator()));
    }
  }

  /** 接收人可以是字符串，也可以是 {recipient, ext} */
  private List<Kv> templateRecipients(Object value) {
    List<Kv> result = new ArrayList<>();
    if (value instanceof Collection) {
      for (Object item : (Collection<?>) value) {
        if (item instanceof Map) {
          Map<?, ?> map = (Map<?, ?>) item;
          result.add(Kv.by("recipient", String.valueOf(map.get("recipient"))).set("ext", map.get("ext")));
        } else {
          result.add(Kv.by("recipient", String.valueOf(item)));
        }
      }
    }
    return result;
  }

  private LocalDateTime sendTime(Map<String, Object> data) {
    Object value = data.get("sendTime");
    if (value == null || value.toString().isEmpty()) {
      return null;
    }
    LocalDateTime time = TimeParams.parse(value.toString(), null);
    ChatErrors.require(time != null, "发送时间格式错误");
    ChatErrors.require(LocalDateTime.now().plusMinutes(MIN_DELAY_MINUTES).isBefore(time), "定时发送时间至少在当前时间的5分钟之后");
    return time;
  }

  private Row msgRow(long id, Map<String, Object> data, LocalDateTime sendTime) {
    return Row.by("id", id).set("send_time", sendTime).set("biz_id", data.get("bizId")).set("biz_type", data.get("bizType"))
        .set("author", data.get("author")).set("create_by", operator());
  }

  private Long operator() {
    return LoginContext.get() == null ? 0L : LoginContext.get().getUserId();
  }
}
