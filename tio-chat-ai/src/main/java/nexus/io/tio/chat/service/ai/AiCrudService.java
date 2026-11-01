package nexus.io.tio.chat.service.ai;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.service.base.crud.TableMeta;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * AI 模块的通用增删改查：逻辑删除列 deleted，审计列 creator/updater，分页参数 pageNo/pageSize，返回 {list, total}
 */
public class AiCrudService {
  private static final Set<String> AUDIT = Set.of("creator", "create_time", "updater", "update_time", "deleted", "tenant_id");
  private static final Set<String> PAGE_KEYS = Set.of("pageNo", "pageSize", "createTime");

  public Long uid() {
    LoginSession session = LoginContext.get();
    return session == null ? null : session.getUid();
  }

  public long create(String table, Map<String, Object> body, boolean bindUser) {
    Row row = toRow(table, body);
    long id = SnowflakeIdUtils.id();
    row.set("id", id);
    if (bindUser && TableMeta.hasColumn(table, "user_id") && uid() != null) {
      row.set("user_id", uid());
    }
    String operator = uid() == null ? "" : String.valueOf(uid());
    row.set("creator", operator).set("updater", operator).set("create_time", LocalDateTime.now())
        .set("update_time", LocalDateTime.now()).set("deleted", 0);
    if (TableMeta.hasColumn(table, "tenant_id")) {
      row.set("tenant_id", 1L);
    }
    Db.save(table, row);
    return id;
  }

  public boolean update(String table, Map<String, Object> body, Long ownerUid) {
    Long id = Values.toLong(body.get("id"));
    ChatErrors.require(id != null, "id不能为空");
    Kv old = rawRow(table, id);
    ChatErrors.require(old != null, "数据不存在");
    if (ownerUid != null && TableMeta.hasColumn(table, "user_id")) {
      ChatErrors.require(ownerUid.equals(old.getLong("user_id")), "数据不存在");
    }
    Row row = toRow(table, body);
    row.remove("id");
    row.set("updater", uid() == null ? "" : String.valueOf(uid())).set("update_time", LocalDateTime.now());
    List<Object> params = new ArrayList<>();
    StringBuilder sql = new StringBuilder("update ").append(table).append(" set ");
    boolean first = true;
    for (Map.Entry<String, Object> entry : row.getColumns().entrySet()) {
      sql.append(first ? "" : ", ").append(entry.getKey()).append("=?");
      params.add(entry.getValue());
      first = false;
    }
    sql.append(" where id=?");
    params.add(id);
    return Db.update(sql.toString(), params.toArray()) > 0;
  }

  public boolean delete(String table, Long id, Long ownerUid) {
    StringBuilder sql = new StringBuilder("update ").append(table).append(" set deleted=1 where id=?");
    if (ownerUid != null && TableMeta.hasColumn(table, "user_id")) {
      sql.append(" and user_id=").append(ownerUid);
    }
    return Db.update(sql.toString(), id) > 0;
  }

  public Kv get(String table, Long id, Long ownerUid) {
    Kv row = rawRow(table, id);
    if (row == null) {
      return null;
    }
    if (ownerUid != null && TableMeta.hasColumn(table, "user_id") && !ownerUid.equals(row.getLong("user_id"))) {
      return null;
    }
    return toVo(table, row);
  }

  public Kv rawRow(String table, Long id) {
    return Db.findFirstMap("select * from " + table + " where id=? and deleted=0", id);
  }

  public List<Kv> listByIds(String table, Collection<Long> ids, Long ownerUid) {
    List<Kv> result = new ArrayList<>();
    if (ids == null || ids.isEmpty()) {
      return result;
    }
    List<Object> params = new ArrayList<>(ids);
    String sql = "select * from " + table + " where deleted=0 and id in (" + Values.placeholders(ids.size()) + ")";
    if (ownerUid != null) {
      sql += " and user_id=?";
      params.add(ownerUid);
    }
    for (Kv row : Db.findMaps(sql + " order by id desc", params.toArray())) {
      result.add(toVo(table, row));
    }
    return result;
  }

  public List<Kv> list(String table, String where, Object... params) {
    List<Kv> result = new ArrayList<>();
    for (Kv row : Db.findMaps("select * from " + table + " where deleted=0" + (where == null ? "" : " and " + where), params)) {
      result.add(toVo(table, row));
    }
    return result;
  }

  /**
   * 分页：查询参数中与列同名的属性作为过滤条件，likeColumns 中的列做模糊匹配
   */
  public Kv page(String table, Map<String, Object> query, Set<String> likeColumns, String extraWhere, Object... extraParams) {
    int pageNo = Values.toInt(query.get("pageNo")) == null ? 1 : Values.toInt(query.get("pageNo"));
    int pageSize = Values.toInt(query.get("pageSize")) == null ? 10 : Values.toInt(query.get("pageSize"));
    StringBuilder where = new StringBuilder(" where deleted=0");
    List<Object> params = new ArrayList<>();
    for (Map.Entry<String, Object> entry : query.entrySet()) {
      if (PAGE_KEYS.contains(entry.getKey())) {
        continue;
      }
      String column = TableMeta.column(table, entry.getKey());
      Object value = entry.getValue();
      if (column == null || value == null || value.toString().isEmpty()) {
        continue;
      }
      if (likeColumns.contains(column)) {
        where.append(" and ").append(column).append(" like ?");
        params.add("%" + value + "%");
      } else {
        where.append(" and ").append(column).append("=?");
        params.add("true".equals(value) ? 1 : "false".equals(value) ? 0 : value);
      }
    }
    if (extraWhere != null) {
      where.append(" and ").append(extraWhere);
      for (Object param : extraParams) {
        params.add(param);
      }
    }
    long total = Db.queryLong("select count(*) from " + table + where, params.toArray());
    params.add((pageNo - 1) * pageSize);
    params.add(pageSize);
    List<Kv> list = new ArrayList<>();
    for (Kv row : Db.findMaps("select * from " + table + where + " order by id desc limit ?, ?", params.toArray())) {
      list.add(toVo(table, row));
    }
    return Kv.by("list", list).set("total", total);
  }

  public Kv toVo(String table, Kv row) {
    Kv vo = Kv.create();
    for (Map.Entry<String, String> entry : TableMeta.fields(table).entrySet()) {
      String column = entry.getValue();
      if ("deleted".equals(column)) {
        continue;
      }
      Object value = row.get(column);
      if (value instanceof LocalDateTime || value instanceof Timestamp) {
        value = Values.time(value);
      }
      // 模型的 publicStatus 是数字（0 公开、1 私有），其他表的 bit 列按布尔返回
      if (value instanceof Boolean && "ai_model".equals(table) && "public_status".equals(column)) {
        value = (Boolean) value ? 1 : 0;
      }
      vo.set(entry.getKey(), value);
    }
    return vo;
  }

  private Row toRow(String table, Map<String, Object> body) {
    Row row = new Row();
    for (Map.Entry<String, Object> entry : body.entrySet()) {
      String column = TableMeta.column(table, entry.getKey());
      if (column == null || AUDIT.contains(column)) {
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

  // ---------------------------------------------------------------- API 密钥

  private static final String MASK = "****";

  /**
   * 密钥明文只返回给创建者，其他人看到掩码
   */
  public Kv maskApiKey(Kv vo, Long viewer) {
    if (vo == null) {
      return null;
    }
    String key = vo.getStr("apiKey");
    if (key != null && (viewer == null || !viewer.equals(vo.getLong("userId")))) {
      vo.set("apiKey", key.length() <= 8 ? MASK : key.substring(0, 3) + MASK + key.substring(key.length() - 4));
    }
    return vo;
  }

  public List<Kv> maskApiKeys(List<Kv> list, Long viewer) {
    for (Kv vo : list) {
      maskApiKey(vo, viewer);
    }
    return list;
  }

  /**
   * 更新时带回的掩码密钥不覆盖原值
   */
  public Map<String, Object> dropMaskedApiKey(Map<String, Object> body) {
    Object key = body.get("apiKey");
    if (key != null && key.toString().contains(MASK)) {
      body.remove("apiKey");
    }
    return body;
  }
}
