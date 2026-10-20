package nexus.io.tio.chat.service.im;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.service.oauth.LoginLogService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.chat.ws.OnlineService;
import nexus.io.tio.chat.ws.PushService;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * 表情包、在线状态、隐私设置与管理端统计
 */
public class ImExtraService {
  private static final String[] PRIVACY_FIELDS = { "isPrivate", "allowTempSession", "searchableByPhone",
      "searchableByAccount", "searchableByUsername", "showOnlineStatus", "allowAddFriend", "allowGroupInvite",
      "hideProfile" };
  private static final String[] PRIVACY_COLUMNS = { "is_private", "allow_temp_session", "searchable_by_phone",
      "searchable_by_account", "searchable_by_username", "show_online_status", "allow_add_friend", "allow_group_invite",
      "hide_profile" };

  // ---------------------------------------------------------------- 表情包

  public List<Kv> emojis(long uid) {
    List<Kv> result = new ArrayList<>();
    for (Kv row : Db.findMaps("select id, expression_url from im_user_emoji where uid=? and is_del=0 order by id", uid)) {
      result.add(Kv.by("id", row.getLong("id")).set("expressionUrl", row.getStr("expression_url")));
    }
    return result;
  }

  public Kv addEmoji(long uid, String url) {
    ChatErrors.require(url != null && !url.isBlank(), "表情地址不能为空");
    Long exists = Db.queryLong("select count(*) from im_user_emoji where uid=? and expression_url=? and is_del=0", uid, url);
    ChatErrors.require(exists == 0, "当前表情已存在哦~~");
    Row row = Row.by("uid", uid).set("expression_url", url).set("create_by", uid);
    DbRows.save("im_user_emoji", row);
    return Kv.by("id", row.getLong("id"));
  }

  public void removeEmoji(long uid, Long id) {
    Kv emoji = Db.findFirstMap("select uid from im_user_emoji where id=? and is_del=0", id);
    ChatErrors.require(emoji != null, "表情不能为空");
    ChatErrors.require(emoji.getLong("uid") == uid, "别人的表情不能删除");
    Db.update("update im_user_emoji set is_del=1 where id=?", id);
  }

  // ---------------------------------------------------------------- 在线状态

  public List<Kv> states() {
    List<Kv> result = new ArrayList<>();
    for (Kv row : Db.findMaps("select * from im_user_state where is_del=0 order by id")) {
      result.add(Kv.by("id", row.getLong("id")).set("title", row.getStr("title")).set("url", row.getStr("url"))
          .set("createTime", Values.time(row.get("create_time"))).set("updateTime", Values.time(row.get("update_time"))));
    }
    return result;
  }

  /**
   * 切换状态后推送给所在群的在线成员
   */
  public boolean changeState(long uid, Long stateId) {
    Long current = Db.queryLong("select user_state_id from im_user where id=?", uid);
    if (current != null && current.equals(stateId)) {
      return true;
    }
    Db.update("update im_user set user_state_id=? where id=?", stateId, uid);
    Long exists = Db.queryLong("select id from im_user_state where id=?", stateId);
    Kv data = Kv.by("uid", uid).set("userStateId", exists == null ? 0L : exists);
    String sql = """
        select g.room_id from im_group_member m join im_room_group g on g.id=m.group_id
        where m.uid=? and m.is_del=0 and g.is_del=0
        """;
    List<Long> roomIds = Db.query(sql, uid);
    OnlineService onlineService = Aop.get(OnlineService.class);
    Set<Long> receivers = new HashSet<>();
    for (Long roomId : roomIds) {
      receivers.addAll(onlineService.groupOnlineMembers(roomId));
    }
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.USER_STATE_CHANGE, data), receivers);
    return true;
  }

  // ---------------------------------------------------------------- 隐私

  public Kv privacy(Long uid) {
    Kv row = Db.findFirstMap("select * from im_user_privacy where uid=? and is_del=0", uid);
    if (row == null) {
      DbRows.save("im_user_privacy", Row.by("uid", uid).set("is_private", 0).set("allow_temp_session", 1)
          .set("searchable_by_phone", 1).set("searchable_by_account", 1).set("searchable_by_username", 1)
          .set("show_online_status", 1).set("allow_add_friend", 1).set("allow_group_invite", 1).set("hide_profile", 0)
          .set("create_by", uid));
      row = Db.findFirstMap("select * from im_user_privacy where uid=? and is_del=0", uid);
    }
    Kv result = Kv.by("id", row.getLong("id")).set("uid", row.getLong("uid"));
    for (int i = 0; i < PRIVACY_FIELDS.length; i++) {
      result.set(PRIVACY_FIELDS[i], Values.toBool(row.get(PRIVACY_COLUMNS[i])));
    }
    result.set("tenantId", row.getLong("tenant_id")).set("createTime", Values.time(row.get("create_time")))
        .set("updateTime", Values.time(row.get("update_time")));
    return result;
  }

  public boolean updatePrivacy(Long uid, Kv req) {
    privacy(uid);
    StringBuilder sql = new StringBuilder("update im_user_privacy set update_by=?");
    List<Object> params = new ArrayList<>();
    params.add(uid);
    for (int i = 0; i < PRIVACY_FIELDS.length; i++) {
      if (req.get(PRIVACY_FIELDS[i]) != null) {
        sql.append(", ").append(PRIVACY_COLUMNS[i]).append("=?");
        params.add(Values.isTrue(req.get(PRIVACY_FIELDS[i])));
      }
    }
    sql.append(" where uid=?");
    params.add(uid);
    return Db.update(sql.toString(), params.toArray()) > 0;
  }

  // ---------------------------------------------------------------- 管理端统计

  public Kv homeStats() {
    LocalDateTime todayStart = LocalDateTime.now().with(LocalTime.MIN);
    LocalDateTime todayEnd = LocalDateTime.now().with(LocalTime.MAX);
    LocalDateTime weekStart = LocalDateTime.now().with(DayOfWeek.MONDAY).with(LocalTime.MIN);
    long activeToday = Db.queryLong("select count(*) from im_user where last_opt_time between ? and ?", todayStart, todayEnd);
    long totalGroup = Db.queryLong("select count(*) from im_room_group where is_del=0");
    long blackCount = Db.queryLong("select count(*) from im_black where deadline>=now()");
    long todayBlack = Db.queryLong("select count(*) from im_black where create_time between ? and ?", todayStart, todayEnd);
    long weekBlack = Db.queryLong("select count(*) from im_black where create_time between ? and ?", weekStart, todayEnd);
    long month3 = Aop.get(LoginLogService.class).countUsersWithMinLogins(LocalDateTime.now().minusDays(30).with(LocalTime.MIN),
        todayEnd, 3);
    return Kv.by("todayActiveUser", (int) activeToday).set("totalGroup", (int) totalGroup).set("blackCount", (int) blackCount)
        .set("aiCallToday", 0)
        .set("blackStats", Kv.by("todayNew", (int) todayBlack).set("weekNew", (int) weekBlack).set("total", (int) blackCount))
        .set("aiStats", Kv.by("todayCalls", 0).set("weekCalls", 0).set("activeModels", 0))
        .set("monthlyLogin3PlusUserCount", (int) month3);
  }

  public List<Kv> loginRank(LocalDateTime start, LocalDateTime end, int limit) {
    List<Kv> result = new ArrayList<>();
    for (Kv row : Aop.get(LoginLogService.class).loginRank(start, end, limit)) {
      Kv user = Db.findFirstMap("select account, name from im_user where id=?", row.getLong("uid"));
      result.add(Kv.by("userId", row.getLong("uid")).set("username", user == null ? null : user.getStr("account"))
          .set("nickName", user == null ? null : user.getStr("name")).set("total", row.getLong("total")));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  public List<Kv> activeUsers(LocalDateTime start, LocalDateTime end, int limit) {
    LoginLogService loginLogService = Aop.get(LoginLogService.class);
    List<Kv> rank = loginLogService.loginRank(start, end, limit);
    Map<Long, Long> counts = new HashMap<>();
    for (Kv row : rank) {
      counts.put(row.getLong("uid"), row.getLong("total"));
    }
    List<Kv> result = new ArrayList<>();
    for (Long uid : loginLogService.uids(rank)) {
      Kv u = Db.findFirstMap("select * from im_user where id=?", uid);
      if (u == null) {
        continue;
      }
      String ip = null;
      String location = null;
      String isp = null;
      if (u.getStr("ip_info") != null) {
        Map<String, Object> info = JsonUtils.parseToMap(u.getStr("ip_info"), String.class, Object.class);
        ip = info.get("updateIp") == null ? null : info.get("updateIp").toString();
        if (info.get("updateIpDetail") instanceof Map) {
          Map<String, Object> detail = (Map<String, Object>) info.get("updateIpDetail");
          StringBuilder sb = new StringBuilder();
          for (String key : new String[] { "country", "region", "city" }) {
            Object v = detail.get(key);
            if (v != null && !v.toString().isEmpty()) {
              sb.append(v);
            }
          }
          location = sb.length() > 0 ? sb.toString() : null;
          isp = detail.get("isp") == null ? null : detail.get("isp").toString();
        }
      }
      result.add(Kv.by("username", u.getStr("account")).set("nickName", u.getStr("name")).set("avatar", u.getStr("avatar"))
          .set("lastOptTime", Values.time(u.get("last_opt_time"))).set("ip", ip).set("location", location).set("isp", isp)
          .set("loginTimes", counts.getOrDefault(uid, 0L).intValue()));
    }
    result.sort(Comparator.comparing((Kv k) -> k.getInt("loginTimes")).reversed());
    return result;
  }
}
