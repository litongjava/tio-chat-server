package nexus.io.tio.chat.service.oauth;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.utils.thread.TioThreadUtils;

/**
 * 登录日志：status 01 登录成功，03 账号密码错误；employee_id 记录 IM uid
 */
public class LoginLogService {
  public static final String SUCCESS = "01";
  public static final String PASSWORD_ERROR = "03";

  public void record(Long uid, Kv user, String ip, String ua, String status, String description) {
    TioThreadUtils.execute(() -> {
      Row row = Row.by("employee_id", uid).set("user_id", user == null ? null : user.getLong("id"))
          .set("request_ip", ip).set("nick_name", user == null ? null : user.getStr("nick_name"))
          .set("username", user == null ? null : user.getStr("username")).set("status", status)
          .set("description", description).set("login_date", LocalDate.now().toString()).set("ua", ua)
          .set("create_by", user == null ? 0L : user.getLong("id"));
      DbRows.save("def_login_log", row);
    });
  }

  public List<Kv> loginRank(LocalDateTime start, LocalDateTime end, int limit) {
    String sql = """
        select employee_id as uid, count(*) as total from def_login_log
        where status='01' and employee_id is not null and create_time>=? and create_time<=?
        group by employee_id order by total desc limit ?
        """;
    return Db.findMaps(sql, start, end, limit);
  }

  public long countUsersWithMinLogins(LocalDateTime start, LocalDateTime end, int minTimes) {
    String sql = """
        select count(*) from (
          select employee_id, count(*) total from def_login_log
          where status='01' and create_time>=? and create_time<=? group by employee_id having count(*)>=?
        ) t
        """;
    return Db.queryLong(sql, start, end, minTimes);
  }

  public List<Long> uids(List<Kv> rank) {
    List<Long> uids = new ArrayList<>();
    for (Kv row : rank) {
      uids.add(row.getLong("uid"));
    }
    return uids;
  }
}
