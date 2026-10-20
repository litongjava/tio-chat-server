package nexus.io.tio.chat.service.im.feed;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;

/**
 * 好友标签与标签关系
 */
public class TargetService {

  public boolean save(long uid, String name, String icon) {
    return DbRows.save("im_target", Row.by("u_id", uid).set("name", name).set("icon", icon).set("create_by", uid));
  }

  public boolean edit(Long id, String name, String icon) {
    return Db.update("update im_target set name=coalesce(?, name), icon=coalesce(?, icon) where id=?", name, icon, id) > 0;
  }

  public Kv detail(Long id) {
    Kv row = Db.findFirstMap("select * from im_target where id=? and is_del=0", id);
    if (row == null) {
      return null;
    }
    return Kv.by("id", row.getLong("id")).set("uid", row.getLong("u_id")).set("name", row.getStr("name"))
        .set("icon", row.getStr("icon")).set("createTime", Values.time(row.get("create_time")))
        .set("updateTime", Values.time(row.get("update_time")));
  }

  public boolean remove(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return false;
    }
    return Db.update("update im_target set is_del=1 where id in (" + Values.placeholders(ids.size()) + ")", ids.toArray()) > 0;
  }

  public List<Kv> relDetail(long uid, Long friendId) {
    String sql = """
        select t.id, t.name, t.icon from im_user_target_rel r join im_target t on t.id=r.target_id and t.is_del=0
        where r.u_id=? and r.friend_id=? and r.is_del=0
        """;
    List<Kv> result = new ArrayList<>();
    for (Kv row : Db.findMaps(sql, uid, friendId)) {
      result.add(Kv.by("id", row.getLong("id")).set("name", row.getStr("name")).set("icon", row.getStr("icon")));
    }
    return result;
  }

  public boolean editRel(long uid, Long friendId, List<Long> targetIds) {
    Db.tx(() -> {
      Db.update("delete from im_user_target_rel where u_id=? and friend_id=?", uid, friendId);
      if (targetIds != null) {
        for (Long targetId : targetIds) {
          DbRows.save("im_user_target_rel", Row.by("u_id", uid).set("friend_id", friendId).set("target_id", targetId)
              .set("create_by", uid));
        }
      }
      return true;
    });
    return true;
  }

  /**
   * 标签下的好友
   */
  public List<Long> friendsOfTargets(List<Long> targetIds, long uid) {
    if (targetIds == null || targetIds.isEmpty()) {
      return new ArrayList<>();
    }
    String sql = "select friend_id from im_user_target_rel where u_id=? and is_del=0 and target_id in ("
        + Values.placeholders(targetIds.size()) + ")";
    List<Object> params = new ArrayList<>();
    params.add(uid);
    params.addAll(targetIds);
    return Db.query(sql, params.toArray());
  }
}
