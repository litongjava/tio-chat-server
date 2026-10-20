package nexus.io.tio.chat.service.base.crud;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.model.common.IPageResp;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.TimeParams;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 后台通用增删改查：请求参数使用驼峰属性名，按表结构映射到列
 */
public class CrudService {
  private static final String[] AUDIT = { "create_by", "create_time", "update_by", "update_time", "is_del", "tenant_id" };

  // ---------------------------------------------------------------- 写入

  public Kv save(CrudDef def, Map<String, Object> body) {
    Row row = toRow(def, body, true);
    if (row.get("id") == null) {
      row.set("id", SnowflakeIdUtils.id());
    }
    Long operator = operator();
    if (operator != null && TableMeta.hasColumn(def.getTable(), "create_by")) {
      row.set("create_by", operator);
    }
    DbRows.save(def.getTable(), row);
    return get(def, row.getLong("id"));
  }

  public Kv copy(CrudDef def, Long id) {
    Kv source = rawRow(def, id);
    ChatErrors.require(source != null, "数据不存在");
    Row row = new Row();
    for (Object key : source.keySet()) {
      String column = key.toString();
      if (!"id".equals(column) && !isAudit(column)) {
        row.set(column, source.get(key));
      }
    }
    row.set("id", SnowflakeIdUtils.id());
    if (operator() != null && TableMeta.hasColumn(def.getTable(), "create_by")) {
      row.set("create_by", operator());
    }
    DbRows.save(def.getTable(), row);
    return get(def, row.getLong("id"));
  }

  public Kv update(CrudDef def, Map<String, Object> body) {
    Long id = Values.toLong(body.get("id"));
    ChatErrors.require(id != null, "id不能为空");
    Row row = toRow(def, body, false);
    row.remove("id");
    if (TableMeta.hasColumn(def.getTable(), "update_by") && operator() != null) {
      row.set("update_by", operator());
    }
    if (!row.getColumns().isEmpty()) {
      List<Object> params = new ArrayList<>();
      StringBuilder sql = new StringBuilder("update ").append(def.getTable()).append(" set ");
      boolean first = true;
      for (Map.Entry<String, Object> entry : row.getColumns().entrySet()) {
        if (!first) {
          sql.append(", ");
        }
        first = false;
        sql.append(entry.getKey()).append("=?");
        params.add(entry.getValue());
      }
      sql.append(" where id=?");
      params.add(id);
      Db.update(sql.toString(), params.toArray());
    }
    return get(def, id);
  }

  public boolean delete(CrudDef def, Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return true;
    }
    String in = Values.placeholders(ids.size());
    if (TableMeta.hasColumn(def.getTable(), "is_del")) {
      Db.update("update " + def.getTable() + " set is_del=1 where id in (" + in + ")", ids.toArray());
    } else {
      Db.update("delete from " + def.getTable() + " where id in (" + in + ")", ids.toArray());
    }
    return true;
  }

  // ---------------------------------------------------------------- 查询

  public Kv get(CrudDef def, Long id) {
    Kv row = rawRow(def, id);
    return row == null ? null : toVo(def, row);
  }

  public Kv detail(CrudDef def, Long id) {
    Kv vo = get(def, id);
    if (vo != null) {
      Aop.get(EchoService.class).fill(List.of(vo), def.getEcho());
    }
    return vo;
  }

  public List<Kv> query(CrudDef def, Map<String, Object> model) {
    List<Object> params = new ArrayList<>();
    String where = where(def, model, null, params);
    List<Kv> result = new ArrayList<>();
    for (Kv row : Db.findMaps("select * from " + def.getTable() + where + " order by id desc", params.toArray())) {
      result.add(toVo(def, row));
    }
    return result;
  }

  public List<Kv> findByIds(CrudDef def, Collection<Long> ids) {
    List<Kv> result = new ArrayList<>();
    if (ids == null || ids.isEmpty()) {
      return result;
    }
    String sql = "select * from " + def.getTable() + " where id in (" + Values.placeholders(ids.size()) + ")";
    for (Kv row : Db.findMaps(sql, ids.toArray())) {
      result.add(toVo(def, row));
    }
    return result;
  }

  /**
   * PageParams：model、size、current、sort、order、extra
   */
  @SuppressWarnings("unchecked")
  public IPageResp<Kv> page(CrudDef def, Map<String, Object> params) {
    Map<String, Object> model = params.get("model") instanceof Map ? (Map<String, Object>) params.get("model") : Map.of();
    Map<String, Object> extra = params.get("extra") instanceof Map ? (Map<String, Object>) params.get("extra") : Map.of();
    long size = Values.toLong(params.get("size")) == null ? 10 : Values.toLong(params.get("size"));
    long current = Values.toLong(params.get("current")) == null ? 1 : Values.toLong(params.get("current"));
    List<Object> sqlParams = new ArrayList<>();
    String where = where(def, model, extra, sqlParams);
    long total = Db.queryLong("select count(*) from " + def.getTable() + where, sqlParams.toArray());
    String orderBy = orderBy(def, params.get("sort"), params.get("order"));
    sqlParams.add((current - 1) * size);
    sqlParams.add(size);
    List<Kv> records = new ArrayList<>();
    for (Kv row : Db.findMaps("select * from " + def.getTable() + where + orderBy + " limit ?, ?", sqlParams.toArray())) {
      records.add(toVo(def, row));
    }
    Aop.get(EchoService.class).fill(records, def.getEcho());
    return IPageResp.of(records, total, size, current);
  }

  // ---------------------------------------------------------------- 工具

  public Kv toVo(CrudDef def, Kv row) {
    Kv vo = Kv.create();
    for (Map.Entry<String, String> entry : TableMeta.fields(def.getTable()).entrySet()) {
      String column = entry.getValue();
      if (def.getHiddenColumns().contains(column) || "is_del".equals(column)) {
        continue;
      }
      Object value = row.get(column);
      if (value instanceof LocalDateTime || value instanceof Timestamp) {
        value = Values.time(value);
      }
      vo.set(entry.getKey(), value);
    }
    return vo;
  }

  private Kv rawRow(CrudDef def, Long id) {
    String sql = "select * from " + def.getTable() + " where id=?";
    if (TableMeta.hasColumn(def.getTable(), "is_del")) {
      sql += " and is_del=0";
    }
    return Db.findFirstMap(sql, id);
  }

  private Row toRow(CrudDef def, Map<String, Object> body, boolean insert) {
    Row row = new Row();
    for (Map.Entry<String, Object> entry : body.entrySet()) {
      String column = TableMeta.column(def.getTable(), entry.getKey());
      if (column == null || isAudit(column)) {
        continue;
      }
      Object value = entry.getValue();
      if (value instanceof Map || value instanceof Collection) {
        value = JsonUtils.toJson(value);
      }
      row.set(column, value);
    }
    return row;
  }

  private boolean isAudit(String column) {
    for (String audit : AUDIT) {
      if (audit.equals(column)) {
        return true;
      }
    }
    return false;
  }

  private String where(CrudDef def, Map<String, Object> model, Map<String, Object> extra, List<Object> params) {
    StringBuilder where = new StringBuilder(" where 1=1");
    if (TableMeta.hasColumn(def.getTable(), "is_del")) {
      where.append(" and is_del=0");
    }
    if (model != null) {
      for (Map.Entry<String, Object> entry : model.entrySet()) {
        String column = TableMeta.column(def.getTable(), entry.getKey());
        Object value = entry.getValue();
        if (column == null || value == null || (value instanceof String && ((String) value).isEmpty())
            || value instanceof Map || value instanceof Collection) {
          continue;
        }
        if (value instanceof String && def.getLikeColumns().contains(column)) {
          where.append(" and ").append(column).append(" like ?");
          params.add("%" + value + "%");
        } else {
          where.append(" and ").append(column).append("=?");
          params.add(value instanceof Boolean ? ((Boolean) value ? 1 : 0) : value);
        }
      }
    }
    if (extra != null) {
      for (Map.Entry<String, Object> entry : extra.entrySet()) {
        appendExtra(def, entry.getKey(), entry.getValue(), where, params);
      }
    }
    return where.toString();
  }

  private static final String[][] SUFFIXES = { { "_likeLeft", "like_left" }, { "_likeRight", "like_right" },
      { "_like", "like" }, { "_start", ">=" }, { "_end", "<=" }, { "_st", "st" }, { "_ed", "ed" }, { "_ge", ">=" },
      { "_gt", ">" }, { "_le", "<=" }, { "_lt", "<" }, { "_ne", "<>" }, { "_eq", "=" }, { "_notIn", "not in" }, { "_in", "in" } };

  private void appendExtra(CrudDef def, String key, Object value, StringBuilder where, List<Object> params) {
    if (value == null || (value instanceof String && ((String) value).isEmpty())) {
      return;
    }
    for (String[] suffix : SUFFIXES) {
      if (!key.endsWith(suffix[0])) {
        continue;
      }
      String column = TableMeta.column(def.getTable(), key.substring(0, key.length() - suffix[0].length()));
      if (column == null) {
        return;
      }
      String op = suffix[1];
      switch (op) {
      case "like" -> {
        where.append(" and ").append(column).append(" like ?");
        params.add("%" + value + "%");
      }
      case "like_left" -> {
        where.append(" and ").append(column).append(" like ?");
        params.add("%" + value);
      }
      case "like_right" -> {
        where.append(" and ").append(column).append(" like ?");
        params.add(value + "%");
      }
      case "st" -> {
        where.append(" and ").append(column).append(">=?");
        params.add(dayBoundary(value.toString(), true));
      }
      case "ed" -> {
        where.append(" and ").append(column).append("<=?");
        params.add(dayBoundary(value.toString(), false));
      }
      case "in", "not in" -> {
        List<Object> values = value instanceof Collection ? new ArrayList<>((Collection<?>) value)
            : List.of((Object[]) value.toString().split(","));
        if (!values.isEmpty()) {
          where.append(" and ").append(column).append(" ").append(op).append(" (").append(Values.placeholders(values.size()))
              .append(")");
          params.addAll(values);
        }
      }
      default -> {
        where.append(" and ").append(column).append(op).append("?");
        params.add(value);
      }
      }
      return;
    }
  }

  private Object dayBoundary(String value, boolean start) {
    if (value.length() == 10) {
      LocalDate date = LocalDate.parse(value);
      return start ? date.atStartOfDay() : date.atTime(LocalTime.MAX);
    }
    return TimeParams.parse(value, null);
  }

  private String orderBy(CrudDef def, Object sort, Object order) {
    if (sort == null || sort.toString().isBlank()) {
      return " order by id desc";
    }
    String[] sorts = sort.toString().split(",");
    String[] orders = order == null ? new String[0] : order.toString().split(",");
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < sorts.length; i++) {
      String column = TableMeta.column(def.getTable(), sorts[i].trim());
      if (column == null) {
        continue;
      }
      String dir = i < orders.length ? orders[i].trim() : "descending";
      boolean asc = "ascending".equals(dir) || "ascend".equals(dir) || "asc".equals(dir);
      parts.add(column + (asc ? " asc" : " desc"));
    }
    return parts.isEmpty() ? " order by id desc" : " order by " + String.join(", ", parts);
  }

  private Long operator() {
    LoginSession session = LoginContext.get();
    return session == null ? null : session.getUserId();
  }
}
