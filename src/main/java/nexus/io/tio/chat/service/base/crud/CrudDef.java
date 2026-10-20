package nexus.io.tio.chat.service.base.crud;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 一个后台通用增删改查资源的定义：接口前缀、表名、模糊查询列、隐藏列与回显规则
 */
public class CrudDef {
  private final String path;
  private final String table;
  private final Set<String> likeColumns = new HashSet<>();
  private final Set<String> hiddenColumns = new HashSet<>();
  /** 字段名 -> 回显规则：dict:类型、org、position */
  private final Map<String, String> echo = new HashMap<>();
  private boolean cache;
  private boolean excel;
  /** 单条查询由扩展接口提供 */
  private boolean customGet;

  public CrudDef(String path, String table) {
    this.path = path;
    this.table = table;
  }

  public static CrudDef of(String path, String table) {
    return new CrudDef(path, table);
  }

  public CrudDef like(String... columns) {
    for (String column : columns) {
      likeColumns.add(column);
    }
    return this;
  }

  public CrudDef hidden(String... columns) {
    for (String column : columns) {
      hiddenColumns.add(column);
    }
    return this;
  }

  public CrudDef echo(String field, String rule) {
    echo.put(field, rule);
    return this;
  }

  public CrudDef cache() {
    this.cache = true;
    return this;
  }

  public CrudDef excel() {
    this.excel = true;
    return this;
  }

  public CrudDef customGet() {
    this.customGet = true;
    return this;
  }

  public boolean isCustomGet() {
    return customGet;
  }

  public String getPath() {
    return path;
  }

  public String getTable() {
    return table;
  }

  public Set<String> getLikeColumns() {
    return likeColumns;
  }

  public Set<String> getHiddenColumns() {
    return hiddenColumns;
  }

  public Map<String, String> getEcho() {
    return echo;
  }

  public boolean isCache() {
    return cache;
  }

  public boolean isExcel() {
    return excel;
  }
}
