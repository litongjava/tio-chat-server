package nexus.io.tio.chat.service.base.crud;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import nexus.io.db.activerecord.Db;

/**
 * 表结构缓存与字段名转换：列 key_ 对应属性 key，列 nick_name 对应属性 nickName
 */
public class TableMeta {
  private static final Map<String, Map<String, String>> FIELD_TO_COLUMN = new ConcurrentHashMap<>();

  /**
   * 属性名 -> 列名，按表中列的顺序
   */
  public static Map<String, String> fields(String table) {
    return FIELD_TO_COLUMN.computeIfAbsent(table, t -> {
      List<String> columns = Db.query("""
          select column_name from information_schema.columns
          where table_schema=database() and table_name=? order by ordinal_position
          """, t);
      Map<String, String> map = new LinkedHashMap<>();
      for (String column : columns) {
        map.put(toField(column), column);
      }
      return map;
    });
  }

  public static boolean hasColumn(String table, String column) {
    return fields(table).containsValue(column);
  }

  public static String column(String table, String field) {
    return fields(table).get(field);
  }

  public static String toField(String column) {
    String name = column.endsWith("_") ? column.substring(0, column.length() - 1) : column;
    StringBuilder sb = new StringBuilder();
    boolean upper = false;
    for (char c : name.toCharArray()) {
      if (c == '_') {
        upper = true;
      } else {
        sb.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return sb.toString();
  }
}
