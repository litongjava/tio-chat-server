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
  /** 普通用户只能访问这一列等于自己的数据，分页和列表查询对管理员也只返回本人的数据 */
  private String ownerColumn;
  /** 所有者列保存的是 im 用户 id，否则是登录用户 id */
  private boolean ownerByUid;
  /** 普通用户不能写入的列 */
  private final Set<String> protectedColumns = new HashSet<>();
  /** 普通用户新增时写入的固定值 */
  private final Map<String, Object> userDefaults = new HashMap<>();
  private boolean adminCreateOnly;
  private CrudHook hook;
  /** 查询时固定加上的条件，例如字典和字典项在同一张表 */
  private String fixedWhere;
  /** 新增时必须填写的列：列 -> 提示 */
  private final Map<String, String> requiredColumns = new HashMap<>();

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

  public CrudDef ownedBy(String column, boolean byUid) {
    this.ownerColumn = column;
    this.ownerByUid = byUid;
    return this;
  }

  public CrudDef protect(String... columns) {
    for (String column : columns) {
      protectedColumns.add(column);
    }
    return this;
  }

  public CrudDef userDefault(String column, Object value) {
    userDefaults.put(column, value);
    return this;
  }

  public CrudDef adminCreateOnly() {
    this.adminCreateOnly = true;
    return this;
  }

  public CrudDef hook(CrudHook hook) {
    this.hook = hook;
    return this;
  }

  public CrudHook getHook() {
    return hook;
  }

  public CrudDef fixedWhere(String condition) {
    this.fixedWhere = condition;
    return this;
  }

  public String getFixedWhere() {
    return fixedWhere;
  }

  public CrudDef required(String column, String message) {
    requiredColumns.put(column, message);
    return this;
  }

  public Map<String, String> getRequiredColumns() {
    return requiredColumns;
  }

  public String getOwnerColumn() {
    return ownerColumn;
  }

  public boolean isOwnerByUid() {
    return ownerByUid;
  }

  public Set<String> getProtectedColumns() {
    return protectedColumns;
  }

  public Map<String, Object> getUserDefaults() {
    return userDefaults;
  }

  public boolean isAdminCreateOnly() {
    return adminCreateOnly;
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
