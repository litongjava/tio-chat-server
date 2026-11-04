package ${servicePackage};

<#if hasCreateTime || hasUpdateTime>
import java.time.LocalDateTime;
</#if>
import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import ${modelPackage}.${table.entityName};
<#if snowflakeId>
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

  public Kv page(${table.entityName} query, long current, long size) {
    List<Object> params = new ArrayList<>();
    StringBuilder where = new StringBuilder(" where 1=1");
<#if logicDelete>
    where.append(" and is_del=0");
</#if>
    if (query != null) {
<#list modelFields as field>
<#if field.isQuery>
      if (query.get${field.javaField?cap_first}() != null) {
<#if (field.queryType!"EQUAL") == "LIKE">
        where.append(" and ${field.name} like ?");
        params.add("%" + query.get${field.javaField?cap_first}() + "%");
<#else>
        where.append(" and ${field.name}=?");
        params.add(query.get${field.javaField?cap_first}());
</#if>
      }
</#if>
</#list>
    }
    long total = Db.queryLong("select count(*) from " + TABLE + where, params.toArray());
    params.add((current - 1) * size);
    params.add(size);
    List<Kv> rows = Db.findMaps("select * from " + TABLE + where + " order by " + PK + " desc limit ?, ?", params.toArray());
    List<Kv> records = new ArrayList<>();
    for (Kv row : rows) {
      records.add(toVo(row));
    }
    return Kv.by("records", records).set("total", total).set("size", size).set("current", current);
  }

  public Kv detail(Object id) {
    String sql = """
        select * from ${table.name} where ${pkField.name}=?<#if logicDelete> and is_del=0</#if>
        """;
    Kv row = Db.findFirstMap(sql, id);
    return row == null ? null : toVo(row);
  }

  public Kv save(${table.entityName} model) {
    Row row = toRow(model);
<#if snowflakeId>
    long id = SnowflakeIdUtils.id();
    row.set(PK, id);
<#elseif !pkField.isIncrement>
    row.set(PK, model.get${pkField.javaField?cap_first}());
</#if>
<#if hasCreateTime>
    row.set("create_time", LocalDateTime.now());
</#if>
<#if hasUpdateTime>
    row.set("update_time", LocalDateTime.now());
</#if>
    Db.save(TABLE, PK, row);
    return detail(row.get(PK));
  }

  public Kv update(${table.entityName} model) {
    Row row = toRow(model);
    row.set(PK, model.get${pkField.javaField?cap_first}());
<#if hasUpdateTime>
    row.set("update_time", LocalDateTime.now());
</#if>
    Db.update(TABLE, PK, row);
    return detail(model.get${pkField.javaField?cap_first}());
  }

  public boolean delete(List<Long> ids) {
    for (Long id : ids) {
<#if logicDelete>
      Db.update("""
          update ${table.name} set is_del=1 where ${pkField.name}=?
          """, id);
<#else>
      Db.update("""
          delete from ${table.name} where ${pkField.name}=?
          """, id);
</#if>
    }
    return true;
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

  private Kv toVo(Kv row) {
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
