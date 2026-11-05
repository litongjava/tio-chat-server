package ${servicePackage};

<#if !hasService>
/**
 * ${(table.swaggerComment!table.name)?replace("*/", "* /")}
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}Service {
}
<#else>
<#if hasCreateTime || hasUpdateTime>
import java.time.LocalDateTime;
</#if>
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.DbPro;
import nexus.io.db.activerecord.Row;
import nexus.io.model.page.Page;
<#if canCache>
import nexus.io.redis.Redis;
</#if>
<#if canCache || canExport>
import nexus.io.tio.utils.json.JsonUtils;
</#if>
<#if tenantLine>
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.utils.environment.EnvUtils;
</#if>
<#if hasEcho>
import ${enumPackage}.${table.entityName}Echo;
</#if>
import ${modelPackage}.${table.entityName};
<#if mainSub>
import ${sub.modelPackage}.${sub.table.entityName};
<#if sub.servicePackage != servicePackage>
import ${sub.servicePackage}.${sub.table.entityName}Service;
</#if>
</#if>
<#if snowflakeId || (mainSub && sub.snowflakeId)>
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;
</#if>

/**
 * ${(table.swaggerComment!table.name)?replace("*/", "* /")}
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}Service {
  private static final String TABLE = "${table.name}";
  private static final String PK = "${pkField.name}";
  private static final int MAX_PAGE_SIZE = 1000;
  private static final Set<String> NOT_COPIED = Set.of(PK, "create_by", "create_time", "update_by", "update_time", "is_del");
  /** 字段名 -> 列名，只有这些字段可以用来排序 */
  private static final Map<String, String> COLUMNS = new HashMap<>();
<#if canCache>
  /** 单条数据的缓存保存在 Redis 的一个 hash 中，多实例共享，需要配置 redis.host */
  private static final String CACHE_KEY = "gen:cache:" + TABLE;
  private static final long CACHE_SECONDS = 3600;
</#if>

  static {
<#list modelFields as field>
    COLUMNS.put("${field.javaField}", "${field.name}");
</#list>
  }

  private DbPro db() {
    return ${dbCall};
  }
<#if tenantLine>

  /**
   * 网关把当前登录用户的租户写入 tenantId 请求头，并带上内部密钥（chat.internal.secret），没有密钥的请求是绕过网关直接访问的，
   * 其中的租户不可信。没有租户时拒绝访问，不查询其他租户的数据
   */
  private String tenantId() {
    HttpRequest request = TioRequestContext.getRequest();
    String secret = EnvUtils.get("chat.internal.secret", "tio-chat-internal");
    if (request == null || !secret.equals(request.getHeader("x-internal-secret"))) {
      throw new IllegalStateException("只能通过网关访问");
    }
    String tenantId = request.getHeader("tenantid");
    if (tenantId == null || tenantId.isEmpty()) {
      throw new IllegalStateException("缺少租户信息");
    }
    return tenantId;
  }
</#if>

  public Kv page(${table.entityName} query, long current, long size, String sort, String order) {
    long pageNo = Math.max(current, 1);
    long pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    List<Object> params = new ArrayList<>();
    String from = "from " + TABLE + where(query, params) + orderBy(sort, order);
    Page<Row> page = db().paginate((int) pageNo, (int) pageSize, "select *", from, params.toArray());
    List<Kv> records = new ArrayList<>();
    for (Row row : page.getList()) {
      records.add(toVo(row.getColumns()));
    }
<#if hasEcho>
    ${table.entityName}Echo.fill(records);
</#if>
    return Kv.by("records", records).set("total", page.getTotalRow()).set("size", pageSize).set("current", pageNo);
  }

  public List<Kv> query(${table.entityName} query) {
    List<Object> params = new ArrayList<>();
    String sql = "select * from " + TABLE + where(query, params) + " order by <#if hasSortValue>sort_value<#else>" + PK + " desc</#if>";
    return toVoList(db().findMaps(sql, params.toArray()));
  }

  public List<Kv> findByIds(List<${pkField.javaType}> ids) {
    List<Kv> rows = new ArrayList<>();
    if (ids == null) {
      return rows;
    }
    for (${pkField.javaType} id : ids) {
      Kv row = raw(id);
      if (row != null) {
        rows.add(row);
      }
    }
    return toVoList(rows);
  }

  public Kv detail(Object id) {
<#if canCache>
    String cached = Redis.call(jedis -> jedis.hget(CACHE_KEY, String.valueOf(id)));
    if (cached != null) {
      return JsonUtils.parse(cached, Kv.class);
    }
</#if>
    Kv row = raw(id);
    if (row == null) {
      return null;
    }
    Kv vo = toVoList(List.of(row)).get(0);
<#if canCache>
    Redis.call(jedis -> {
      jedis.hset(CACHE_KEY, String.valueOf(id), JsonUtils.toJson(vo));
      return jedis.expire(CACHE_KEY, CACHE_SECONDS);
    });
</#if>
    return vo;
  }
<#if hasTree>

  /** 按 parent_id 组装成树，找不到父节点的数据作为根节点 */
  public List<Kv> tree(${table.entityName} query) {
    List<Kv> list = query(query);
    Map<String, Kv> nodes = new HashMap<>();
    for (Kv node : list) {
      nodes.put(String.valueOf(node.get("${pkField.javaField}")), node);
    }
    List<Kv> roots = new ArrayList<>();
    for (Kv node : list) {
      Kv parent = nodes.get(String.valueOf(node.get("parentId")));
      if (parent == null) {
        roots.add(node);
        continue;
      }
      @SuppressWarnings("unchecked")
      List<Kv> children = (List<Kv>) parent.get("children");
      if (children == null) {
        children = new ArrayList<>();
        parent.set("children", children);
      }
      children.add(node);
    }
    return roots;
  }
</#if>

  public Kv save(${table.entityName} model) {
    Row row = toRow(model);
<#if snowflakeId>
    row.set(PK, SnowflakeIdUtils.id());
<#elseif !pkField.isIncrement>
    row.set(PK, model.get${pkField.javaField?cap_first}());
</#if>
<#if hasCreateTime>
    row.set("create_time", LocalDateTime.now());
</#if>
<#if hasUpdateTime>
    row.set("update_time", LocalDateTime.now());
</#if>
<#if tenantLine>
    row.set("tenant_id", tenantId());
</#if>
<#if mainSub>
    boolean saved = db().tx(() -> {
      db().save(TABLE, PK, row);
      saveItems(model.getInsertList(), row.get(PK));
      return true;
    });
    return saved ? detail(row.get(PK)) : null;
<#else>
    db().save(TABLE, PK, row);
    return detail(row.get(PK));
</#if>
  }

  /** @return 修改后的数据，数据不存在时返回 null */
  public Kv update(${table.entityName} model) {
    Object id = model.get${pkField.javaField?cap_first}();
    if (raw(id) == null) {
      return null;
    }
    Row row = toRow(model);
    row.set(PK, id);
<#if hasUpdateTime>
    row.set("update_time", LocalDateTime.now());
</#if>
<#if mainSub>
    boolean updated = db().tx(() -> {
      if (!ownsItems(model, id)) {
        return false;
      }
      db().update(TABLE, PK, row);
      saveItems(model.getInsertList(), id);
      ${sub.table.entityName}Service items = new ${sub.table.entityName}Service();
      if (model.getUpdateList() != null) {
        for (${sub.table.entityName} item : model.getUpdateList()) {
          item.set${table.subJavaFieldName?cap_first}(<#if subFkType == "String">String.valueOf(id)<#else>${subFkType}.valueOf(String.valueOf(id))</#if>);
          items.update(item);
        }
      }
      if (model.getDeleteList() != null) {
        items.delete(model.getDeleteList());
      }
      return true;
    });
    if (!updated) {
      return null;
    }
<#else>
    db().update(TABLE, PK, row);
</#if>
<#if canCache>
    Redis.call(jedis -> jedis.hdel(CACHE_KEY, String.valueOf(id)));
</#if>
    return detail(id);
  }

  /** @return 复制出来的数据，原数据不存在时返回 null */
  public Kv copy(Object id) {
    Kv source = raw(id);
    if (source == null) {
      return null;
    }
    Row row = copyRow(source);
<#if snowflakeId>
    row.set(PK, SnowflakeIdUtils.id());
</#if>
<#if hasCreateTime>
    row.set("create_time", LocalDateTime.now());
</#if>
<#if hasUpdateTime>
    row.set("update_time", LocalDateTime.now());
</#if>
<#if mainSub>
    boolean copied = db().tx(() -> {
      db().save(TABLE, PK, row);
      copyItems(id, row.get(PK));
      return true;
    });
    return copied ? detail(row.get(PK)) : null;
<#else>
    db().save(TABLE, PK, row);
    return detail(row.get(PK));
</#if>
  }

  public boolean delete(List<${pkField.javaType}> ids) {
<#if mainSub>
    return db().tx(() -> {
      for (${pkField.javaType} id : ids) {
        if (raw(id) != null) {
          deleteItems(id);
          deleteOne(id);
        }
      }
      return true;
    });
  }

  private void deleteOne(Object id) {
<#if logicDelete>
    String sql = "update " + TABLE + " set is_del=1 where " + PK + "=?<#if tenantLine> and tenant_id=?</#if>";
<#else>
    String sql = "delete from " + TABLE + " where " + PK + "=?<#if tenantLine> and tenant_id=?</#if>";
</#if>
    db().update(sql, id<#if tenantLine>, tenantId()</#if>);
<#if canCache>
    Redis.call(jedis -> jedis.hdel(CACHE_KEY, String.valueOf(id)));
</#if>
  }
<#else>
    for (${pkField.javaType} id : ids) {
<#if logicDelete>
      String sql = "update " + TABLE + " set is_del=1 where " + PK + "=?<#if tenantLine> and tenant_id=?</#if>";
<#else>
      String sql = "delete from " + TABLE + " where " + PK + "=?<#if tenantLine> and tenant_id=?</#if>";
</#if>
      db().update(sql, id<#if tenantLine>, tenantId()</#if>);
<#if canCache>
      Redis.call(jedis -> jedis.hdel(CACHE_KEY, String.valueOf(id)));
</#if>
    }
    return true;
  }
</#if>
<#if canCache>

  public void clearCache() {
    Redis.call(jedis -> jedis.del(CACHE_KEY));
  }
</#if>
<#if canExport>

  /** 导出查询到的数据，内容以 = + - @ 开头时加单引号，避免在表格软件中被当作公式执行 */
  public String exportCsv(${table.entityName} query) {
    StringBuilder csv = new StringBuilder("﻿");
<#list modelFields as field>
    csv.append(cell("${(field.swaggerComment?has_content?then(field.swaggerComment, field.name))?j_string}"))<#if field?has_next>.append(',')<#else>.append('\n')</#if>;
</#list>
    for (Kv row : query(query)) {
<#list modelFields as field>
      csv.append(cell(row.get("${field.javaField}")))<#if field?has_next>.append(',')<#else>.append('\n')</#if>;
</#list>
    }
    return csv.toString();
  }

  public String exportHtml(${table.entityName} query) {
    StringBuilder html = new StringBuilder("<table><thead><tr>");
<#list modelFields as field>
    html.append("<th>").append(escapeHtml("${(field.swaggerComment?has_content?then(field.swaggerComment, field.name))?j_string}")).append("</th>");
</#list>
    html.append("</tr></thead><tbody>");
    for (Kv row : query(query)) {
      html.append("<tr>");
<#list modelFields as field>
      html.append("<td>").append(escapeHtml(row.get("${field.javaField}"))).append("</td>");
</#list>
      html.append("</tr>");
    }
    return html.append("</tbody></table>").toString();
  }

  private String escapeHtml(Object value) {
    String text = value == null ? "" : String.valueOf(value);
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  /**
   * 导入导出格式的 CSV：第一行是表头，列的顺序和导出相同。主键列留空时生成新数据，全部在一个事务里
   *
   * @return 导入的行数
   */
  public int importCsv(String csv) {
    List<List<String>> rows = parseCsv(csv.startsWith("\uFEFF") ? csv.substring(1) : csv);
    if (rows.size() < 2) {
      return 0;
    }
    int[] imported = { 0 };
    db().tx(() -> {
      for (List<String> values : rows.subList(1, rows.size())) {
        Kv data = Kv.create();
<#list modelFields as field>
<#if !field.isPk && !field.fill?? && field.modelType != "byte[]">
        if (${field?index} < values.size() && !values.get(${field?index}).isEmpty()) {
          data.set("${field.javaField}", unescapeCell(values.get(${field?index})));
        }
</#if>
</#list>
        save(JsonUtils.parse(JsonUtils.toJson(data), ${table.entityName}.class));
        imported[0]++;
      }
      return true;
    });
    return imported[0];
  }

  /** 去掉导出时为避免公式执行加的单引号 */
  private String unescapeCell(String value) {
    return value.length() > 1 && value.charAt(0) == '\'' && "=+-@".indexOf(value.charAt(1)) >= 0 ? value.substring(1) : value;
  }

  private List<List<String>> parseCsv(String csv) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < csv.length(); i++) {
      char c = csv.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
          cell.append('"');
          i++;
        } else if (c == '"') {
          quoted = false;
        } else {
          cell.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        row.add(cell.toString());
        cell.setLength(0);
      } else if (c == '\n') {
        row.add(cell.toString());
        cell.setLength(0);
        rows.add(row);
        row = new ArrayList<>();
      } else if (c != '\r') {
        cell.append(c);
      }
    }
    if (cell.length() > 0 || !row.isEmpty()) {
      row.add(cell.toString());
      rows.add(row);
    }
    return rows;
  }

  private String cell(Object value) {
    String text = value == null ? "" : String.valueOf(value);
    if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
      text = "'" + text;
    }
    return '"' + text.replace("\"", "\"\"") + '"';
  }
</#if>
<#if mainSub>

  /** 保存新增的从表数据 */
  private void saveItems(List<${sub.table.entityName}> list, Object mainId) {
    if (list == null) {
      return;
    }
    ${sub.table.entityName}Service items = new ${sub.table.entityName}Service();
    for (${sub.table.entityName} item : list) {
      item.set${table.subJavaFieldName?cap_first}(<#if subFkType == "String">String.valueOf(mainId)<#else>${subFkType}.valueOf(String.valueOf(mainId))</#if>);
      items.save(item);
    }
  }

  /** 修改和删除的从表数据必须属于这条主表数据 */
  private boolean ownsItems(${table.entityName} model, Object mainId) {
    List<Object> ids = new ArrayList<>();
    if (model.getUpdateList() != null) {
      for (${sub.table.entityName} item : model.getUpdateList()) {
        ids.add(item.get${sub.pkField.javaField?cap_first}());
      }
    }
    if (model.getDeleteList() != null) {
      ids.addAll(model.getDeleteList());
    }
    String sql = """
        select count(*) from ${sub.table.name} where ${sub.pkField.name}=? and ${subFkColumn}=?<#if sub.logicDelete> and is_del=0</#if>
        """;
    for (Object id : ids) {
      if (db().queryLong(sql, id, mainId) == 0) {
        return false;
      }
    }
    return true;
  }

  private void copyItems(Object mainId, Object newMainId) {
    String sql = """
        select * from ${sub.table.name} where ${subFkColumn}=?<#if sub.logicDelete> and is_del=0</#if>
        """;
    for (Kv item : db().findMaps(sql, mainId)) {
      Row row = new Row();
      for (Object key : item.keySet()) {
        String column = key.toString();
        if (!"${sub.pkField.name}".equals(column) && !NOT_COPIED.contains(column)) {
          row.set(column, item.get(key));
        }
      }
      row.set("${subFkColumn}", newMainId);
<#if sub.snowflakeId>
      row.set("${sub.pkField.name}", SnowflakeIdUtils.id());
</#if>
      db().save("${sub.table.name}", "${sub.pkField.name}", row);
    }
  }

  private void deleteItems(Object mainId) {
<#if sub.logicDelete>
    db().update("""
        update ${sub.table.name} set is_del=1 where ${subFkColumn}=?
        """, mainId);
<#else>
    db().update("""
        delete from ${sub.table.name} where ${subFkColumn}=?
        """, mainId);
</#if>
  }
</#if>

  private Kv raw(Object id) {
    String sql = """
        select * from ${table.name} where ${pkField.name}=?<#if logicDelete> and is_del=0</#if><#if tenantLine> and tenant_id=?</#if>
        """;
    return db().findFirstMap(sql, id<#if tenantLine>, tenantId()</#if>);
  }

  private String where(${table.entityName} query, List<Object> params) {
    StringBuilder where = new StringBuilder(" where 1=1");
<#if logicDelete>
    where.append(" and is_del=0");
</#if>
<#if tenantLine>
    where.append(" and tenant_id=?");
    params.add(tenantId());
</#if>
    if (query == null) {
      return where.toString();
    }
<#list modelFields as field>
<#if field.isQuery && field.modelType != "byte[]">
    if (query.get${field.javaField?cap_first}() != null<#if field.modelType == "String"> && !query.get${field.javaField?cap_first}().isEmpty()</#if>) {
<#if field.condition == "LIKE">
      where.append(" and ${field.name} like ?");
      params.add("%" + query.get${field.javaField?cap_first}() + "%");
<#elseif field.condition == "LIKE_LEFT">
      where.append(" and ${field.name} like ?");
      params.add("%" + query.get${field.javaField?cap_first}());
<#elseif field.condition == "LIKE_RIGHT">
      where.append(" and ${field.name} like ?");
      params.add(query.get${field.javaField?cap_first}() + "%");
<#elseif field.condition == "NOT_EQUAL">
      where.append(" and ${field.name}<>?");
      params.add(query.get${field.javaField?cap_first}());
<#else>
      where.append(" and ${field.name}=?");
      params.add(query.get${field.javaField?cap_first}());
</#if>
    }
</#if>
</#list>
    return where.toString();
  }

  private String orderBy(String sort, String order) {
    String column = sort == null ? null : COLUMNS.get(sort);
    if (column == null) {
      return " order by " + PK + " desc";
    }
    boolean asc = "ascending".equals(order) || "ascend".equals(order) || "asc".equals(order);
    return " order by " + column + (asc ? " asc" : " desc");
  }

  private Row copyRow(Kv source) {
    Row row = new Row();
    for (Object key : source.keySet()) {
      String column = key.toString();
      if (!NOT_COPIED.contains(column)) {
        row.set(column, source.get(key));
      }
    }
    return row;
  }

  private Row toRow(${table.entityName} model) {
    Row row = new Row();
<#list modelFields as field>
<#if !field.isPk && !field.fill??>
    if (model.get${field.javaField?cap_first}() != null) {
      row.set("${field.name}", model.get${field.javaField?cap_first}());
    }
</#if>
</#list>
    return row;
  }

  private List<Kv> toVoList(List<Kv> rows) {
    List<Kv> records = new ArrayList<>();
    for (Kv row : rows) {
      records.add(toVo(row));
    }
<#if hasEcho>
    ${table.entityName}Echo.fill(records);
</#if>
    return records;
  }

  private Kv toVo(Map<?, ?> row) {
    Kv vo = Kv.create();
<#list modelFields as field>
<#if field.javaType == "Long">
    vo.set("${field.javaField}", row.get("${field.name}") == null ? null : String.valueOf(row.get("${field.name}")));
<#else>
    vo.set("${field.javaField}", row.get("${field.name}"));
</#if>
</#list>
    return vo;
  }
}
</#if>
