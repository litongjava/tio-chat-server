package nexus.io.tio.chat.service.base;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;

/**
 * 带 parent_id、tree_path、tree_grade 的树形数据。tree_path 是祖先 id 的路径，例如 /1/5/，根节点是 /
 */
public final class BaseTrees {
  private static final long ROOT_ID = 0L;
  private static final String ROOT_PATH = "/";
  private static final int ROOT_GRADE = 0;

  private BaseTrees() {
  }

  /** 按父级计算 parent_id、tree_path 和 tree_grade。修改时只有传了 parentId 才重新计算 */
  public static void fill(Row row, String table, String label) {
    if (!row.getColumns().containsKey("parent_id")) {
      return;
    }
    Long parentId = Values.toLong(row.get("parent_id"));
    if (parentId == null || parentId <= 0) {
      row.set("parent_id", ROOT_ID).set("tree_path", ROOT_PATH).set("tree_grade", ROOT_GRADE);
      return;
    }
    Kv parent = Db.findFirstMap("select id, tree_path, tree_grade from " + table + " where id=? and is_del=0", parentId);
    ChatErrors.require(parent != null, "请正确填写父级" + label);
    String path = parent.getStr("tree_path") == null ? ROOT_PATH : parent.getStr("tree_path");
    row.set("tree_grade", (parent.getInt("tree_grade") == null ? ROOT_GRADE : parent.getInt("tree_grade")) + 1)
        .set("tree_path", path + parent.getLong("id") + "/");
  }

  /** 修改节点时计算新的路径，不能挂到自己或自己的子节点下，父级变化时同步所有子孙节点的路径和层级 */
  public static void beforeMove(Long id, Row row, String table, String label) {
    if (!row.getColumns().containsKey("parent_id")) {
      return;
    }
    Long parentId = Values.toLong(row.get("parent_id"));
    ChatErrors.require(!id.equals(parentId), "不能把" + label + "移动到自己下面");
    fill(row, table, label);
    String newPath = row.getStr("tree_path");
    ChatErrors.require(!newPath.contains("/" + id + "/"), "不能把" + label + "移动到自己的子节点下面");
    Kv old = Db.findFirstMap("select tree_path, tree_grade from " + table + " where id=?", id);
    if (old == null || old.getStr("tree_path") == null || old.getStr("tree_path").equals(newPath)) {
      return;
    }
    String oldPrefix = old.getStr("tree_path") + id + "/";
    String newPrefix = newPath + id + "/";
    int oldGrade = old.getInt("tree_grade") == null ? ROOT_GRADE : old.getInt("tree_grade");
    int gradeChange = row.getInt("tree_grade") - oldGrade;
    String sql = "update " + table + " set tree_path=concat(?, substring(tree_path, ?)), tree_grade=tree_grade+? where tree_path like ?";
    Db.update(sql, newPrefix, oldPrefix.length() + 1, gradeChange, oldPrefix + "%");
  }
}
