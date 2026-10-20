package nexus.io.tio.chat.utils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 插入记录时补齐通用审计列（id、create_time、create_by、update_by、tenant_id、is_del），
 * 只补表中存在且调用方未设置的列。
 */
public class DbRows {
  private static final Map<String, Set<String>> COLUMNS = new ConcurrentHashMap<>();

  public static boolean save(String table, Row row) {
    Set<String> columns = columns(table);
    if (columns.contains("id") && row.get("id") == null) {
      row.set("id", SnowflakeIdUtils.id());
    }
    Object operator = row.get("create_by");
    if (operator == null) {
      operator = 0L;
      if (columns.contains("create_by")) {
        row.set("create_by", operator);
      }
    }
    if (columns.contains("update_by") && row.get("update_by") == null) {
      row.set("update_by", operator);
    }
    if (columns.contains("create_time") && row.get("create_time") == null) {
      row.set("create_time", LocalDateTime.now());
    }
    if (columns.contains("update_time") && row.get("update_time") == null) {
      row.set("update_time", LocalDateTime.now());
    }
    if (columns.contains("tenant_id") && row.get("tenant_id") == null) {
      row.set("tenant_id", 1L);
    }
    if (columns.contains("is_del") && row.get("is_del") == null) {
      row.set("is_del", 0);
    }
    return Db.save(table, row);
  }

  private static Set<String> columns(String table) {
    return COLUMNS.computeIfAbsent(table, t -> {
      List<String> names = Db.query(
          "select column_name from information_schema.columns where table_schema=database() and table_name=?", t);
      return new HashSet<>(names);
    });
  }
}
