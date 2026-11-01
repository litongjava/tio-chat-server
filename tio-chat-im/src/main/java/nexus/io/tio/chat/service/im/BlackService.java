package nexus.io.tio.chat.service.im;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.tio.chat.model.common.PageBaseResp;
import nexus.io.tio.chat.model.im.user.BlackAddReq;
import nexus.io.tio.chat.model.im.user.BlackEditReq;
import nexus.io.tio.chat.model.im.user.BlackPageResp;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 黑名单：type=1 表示 IP，type=2 表示 uid
 */
public class BlackService {
  public static final int TYPE_IP = 1;
  public static final int TYPE_UID = 2;
  private static final LocalDateTime MAX_DATE = LocalDateTime.of(2099, 12, 31, 0, 0, 0);

  public void add(BlackAddReq req, long operator) {
    ChatErrors.require(req.getType() != null && req.getTarget() != null, "参数错误");
    String target = req.getTarget().trim();
    LocalDateTime deadline = req.getDeadline() == null || req.getDeadline() == 0 ? MAX_DATE
        : LocalDateTime.now().plusMinutes(req.getDeadline());
    Long id = Db.queryLong("select id from im_black where type=? and target=? and is_del=0 limit 1", req.getType(),
        target);
    if (id != null) {
      Db.update("update im_black set deadline=?, update_by=? where id=?", deadline, operator, id);
      return;
    }
    Row row = Row.by("id", SnowflakeIdUtils.id()).set("type", req.getType()).set("target", target)
        .set("deadline", deadline).set("create_by", operator).set("tenant_id", 1L);
    DbRows.save("im_black", row);
  }

  public void edit(BlackEditReq req, long operator) {
    Kv black = Db.findFirstMap("select id from im_black where id=? and is_del=0", req.getId());
    ChatErrors.require(black != null, "黑名单记录不存在");
    LocalDateTime deadline = MAX_DATE;
    if (req.getDeadline() != null && !req.getDeadline().isBlank()) {
      try {
        deadline = LocalDateTime.parse(req.getDeadline(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      } catch (Exception e) {
        throw ChatErrors.of("截止时间格式错误，请使用 yyyy-MM-dd HH:mm:ss 格式");
      }
    }
    Db.update("update im_black set type=?, target=?, deadline=?, update_by=? where id=?", req.getType(),
        req.getTarget(), deadline, operator, req.getId());
  }

  public void remove(Long id) {
    Kv black = Db.findFirstMap("select id from im_black where id=? and is_del=0", id);
    ChatErrors.require(black != null, "黑名单记录不存在");
    Db.update("delete from im_black where id=?", id);
  }

  public PageBaseResp<BlackPageResp> page(String keyword, int pageNo, int pageSize) {
    String search = keyword == null ? null : keyword.trim();
    long total = Db.queryLong("select count(*) from im_black where is_del=0");
    List<Kv> rows = Db.findMaps("select * from im_black where is_del=0 order by create_time desc limit ?, ?",
        (pageNo - 1) * pageSize, pageSize);
    List<BlackPageResp> list = new ArrayList<>();
    for (Kv row : rows) {
      BlackPageResp resp = new BlackPageResp(row.getLong("id"), Values.toInt(row.get("type")), row.getStr("target"),
          Values.time(row.get("deadline")), Values.time(row.get("create_time")), null);
      if (Integer.valueOf(TYPE_UID).equals(resp.getType())) {
        Long uid = parseLong(resp.getTarget());
        if (uid != null) {
          resp.setUserName(Db.queryStr("select name from im_user where id=?", uid));
        }
      }
      if (search != null && !search.isEmpty()) {
        boolean hit = Integer.valueOf(TYPE_IP).equals(resp.getType()) ? resp.getTarget().contains(search)
            : resp.getUserName() != null && resp.getUserName().contains(search);
        if (!hit) {
          continue;
        }
      }
      list.add(resp);
    }
    if (search != null && !search.isEmpty()) {
      total = list.size();
    }
    return PageBaseResp.init(pageNo, pageSize, total, list);
  }

  public boolean isBlack(Long uid, String ip) {
    String sql = """
        select count(*) from im_black
        where is_del=0 and deadline>now() and ((type=? and target=?) or (type=? and target=?))
        """;
    Long count = Db.queryLong(sql, TYPE_UID, uid == null ? "" : uid.toString(), TYPE_IP, ip == null ? "" : ip);
    return count != null && count > 0;
  }

  private Long parseLong(String value) {
    try {
      return Long.parseLong(value);
    } catch (Exception e) {
      return null;
    }
  }
}
