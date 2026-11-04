package nexus.io.tio.chat.service.generator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.model.common.IPageResp;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.chat.service.base.crud.CrudService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 代码生成的表配置：从数据源导入、同步字段、增删改查
 */
public class GenTableService {
  public static final CrudDef TABLE = CrudDef.of("/defGenTable", "def_gen_table").like("name", "comment_", "swagger_comment",
      "author", "entity_name", "service_name", "module_name");
  public static final CrudDef COLUMN = CrudDef.of("/defGenTableColumn", "def_gen_table_column").like("name", "comment_",
      "swagger_comment", "java_field");
  private static final String[] SINGLE_TABLE_FIELDS = { "entityName", "comment", "swaggerComment", "menuName" };

  private final GenDbMeta meta = new GenDbMeta();

  private CrudService crud() {
    return Aop.get(CrudService.class);
  }

  public List<Kv> selectTableList(Long dsId) {
    return meta.tables(dsId);
  }

  public boolean importCheck(List<String> names) {
    ChatErrors.require(names != null && !names.isEmpty(), "请先勾选需要导入的表");
    List<String> exists = existing(names);
    ChatErrors.require(exists.isEmpty(), "表：" + String.join(",", exists) + " 已经存在，是否覆盖导入？");
    return true;
  }

  private List<String> existing(List<String> names) {
    List<Object> params = new ArrayList<>(names);
    String sql = "select distinct name from def_gen_table where is_del=0 and name in (" + Values.placeholders(names.size()) + ")";
    List<String> result = new ArrayList<>();
    for (Kv row : Db.findMaps(sql, params.toArray())) {
      result.add(row.getStr("name"));
    }
    return result;
  }

  /** 导入表和字段，已经导入过的表被覆盖 */
  public boolean importTable(Long dsId, List<String> names) {
    ChatErrors.require(names != null && !names.isEmpty(), "请先选择需要导入的表");
    List<Kv> metas = new ArrayList<>();
    for (String name : names) {
      metas.add(meta.table(dsId, name));
    }
    return Db.tx(() -> {
      for (Kv tableMeta : metas) {
        for (Kv old : Db.findMaps("select id from def_gen_table where name=? and is_del=0", tableMeta.getStr("name"))) {
          delete(List.of(old.getLong("id")));
        }
        importOne(dsId, tableMeta);
      }
      return true;
    });
  }

  @SuppressWarnings("unchecked")
  private void importOne(Long dsId, Kv tableMeta) {
    String name = tableMeta.getStr("name");
    String swagger = GenColumns.swaggerComment(tableMeta.getStr("comment"));
    long id = SnowflakeIdUtils.id();
    Row table = Row.by("id", id).set("name", name).set("comment_", tableMeta.getStr("comment")).set("swagger_comment", swagger)
        .set("ds_id", dsId == null ? 0L : dsId).set("author", GenConfig.author()).set("entity_name", entityName(name))
        .set("entity_super_class", GenEnums.ENTITY_ENTITY).set("super_class", GenEnums.SUPER_CLASS)
        .set("parent", GenConfig.parent()).set("plus_application_name", "").set("plus_module_name", "").set("service_name", "")
        .set("module_name", "").set("child_package_name", "").set("is_tenant_line", false).set("ds_value", "").set("is_ds", false)
        .set("is_lombok", true).set("is_chain", false).set("is_column_constant", false).set("gen_type", GenEnums.GEN_DIRECT)
        .set("output_dir", GenConfig.outputDir()).set("front_output_dir", GenConfig.frontOutputDir())
        .set("front_soy_output_dir", GenConfig.frontSoyOutputDir()).set("front_vben5_output_dir", GenConfig.frontVben5OutputDir())
        .set("tpl_type", GenEnums.TPL_SIMPLE).set("popup_type", GenEnums.POPUP_MODAL).set("add_show", true).set("copy_show", true)
        .set("edit_show", true).set("delete_show", true).set("view_show", true).set("menu_name", swagger + "维护");
    DbRows.save(TABLE.getTable(), table);
    Kv tableKv = Kv.by("id", id).set("entity_name", table.getStr("entity_name"));
    int sort = 0;
    for (Kv column : (List<Kv>) tableMeta.get("columns")) {
      saveColumn(tableKv, column, sort++);
    }
  }

  private void saveColumn(Kv table, Kv column, int sort) {
    Row row = GenColumns.init(table, column).set("id", SnowflakeIdUtils.id()).set("table_id", table.getLong("id"))
        .set("sort_value", sort);
    DbRows.save(COLUMN.getTable(), row);
  }

  private String entityName(String tableName) {
    String name = tableName;
    for (String prefix : GenConfig.tablePrefix().split(",")) {
      if (!prefix.isEmpty() && name.startsWith(prefix)) {
        name = name.substring(prefix.length());
        break;
      }
    }
    return GenColumns.capitalize(GenColumns.javaField(name));
  }

  /** 同步表结构：新增的字段加入配置，已删除的字段移除，已有字段的配置保持不变 */
  public boolean syncField(Long id) {
    Kv table = Db.findFirstMap("select * from def_gen_table where id=? and is_del=0", id);
    ChatErrors.require(table != null, "请先选择需要导入的表");
    List<Kv> columns = meta.columns(table.getLong("ds_id"), table.getStr("name"));
    ChatErrors.require(!columns.isEmpty(), "请确保该表的数据源配置正确或该表中至少存在1个字段");
    return Db.tx(() -> {
      Map<String, Long> existing = new HashMap<>();
      for (Kv row : Db.findMaps("select id, name from def_gen_table_column where table_id=? and is_del=0", id)) {
        existing.put(row.getStr("name"), row.getLong("id"));
      }
      Set<String> names = new HashSet<>();
      int sort = existing.size();
      for (Kv column : columns) {
        names.add(column.getStr("name"));
        if (!existing.containsKey(column.getStr("name"))) {
          saveColumn(table, column, sort++);
        }
      }
      existing.forEach((name, columnId) -> {
        if (!names.contains(name)) {
          Db.update("update def_gen_table_column set is_del=1 where id=?", columnId);
        }
      });
      return true;
    });
  }

  /** 按数据库重新初始化一个字段的配置 */
  public boolean syncColumn(Long tableId, Long id) {
    Kv table = Db.findFirstMap("select * from def_gen_table where id=? and is_del=0", tableId);
    Kv current = Db.findFirstMap("select * from def_gen_table_column where id=? and table_id=? and is_del=0", id, tableId);
    ChatErrors.require(table != null, "请先选择需要同步的表");
    ChatErrors.require(current != null, "请先选择需要同步的字段");
    for (Kv column : meta.columns(table.getLong("ds_id"), table.getStr("name"))) {
      if (current.getStr("name").equals(column.getStr("name"))) {
        Row row = GenColumns.init(table, column);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("update def_gen_table_column set ");
        row.getColumns().forEach((name, value) -> {
          sql.append(params.isEmpty() ? "" : ", ").append(name).append("=?");
          params.add(value);
        });
        params.add(id);
        Db.update(sql.append(" where id=?").toString(), params.toArray());
        return true;
      }
    }
    throw ChatErrors.of("字段已不存在，请先同步表结构");
  }

  // ---------------------------------------------------------------- 增删改查

  public Kv save(Map<String, Object> body) {
    return GenEnums.toApi(crud().save(TABLE, GenEnums.toDb(body)));
  }

  public Kv copy(Long id) {
    return GenEnums.toApi(crud().copy(TABLE, id));
  }

  /** tableIdList 不为空时批量修改，批量时不修改每张表各自的名称和说明 */
  public Kv update(Map<String, Object> body) {
    Map<String, Object> data = GenEnums.toDb(body);
    List<Long> ids = data.get("tableIdList") instanceof Collection<?> list ? Values.toLongList(list) : List.of();
    data.remove("tableIdList");
    if (ids.isEmpty()) {
      ChatErrors.require(Values.toLong(data.get("id")) != null, "ID不能为空");
      return GenEnums.toApi(crud().update(TABLE, data));
    }
    data.remove("name");
    data.remove("dsId");
    if (ids.size() > 1) {
      for (String field : SINGLE_TABLE_FIELDS) {
        data.remove(field);
      }
    }
    return Db.tx(() -> {
      for (Long id : ids) {
        data.put("id", id);
        crud().update(TABLE, data);
      }
      return true;
    }) ? GenEnums.toApi(crud().get(TABLE, ids.get(0))) : null;
  }

  /** 删除表配置和它的字段 */
  public boolean delete(Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return true;
    }
    return Db.tx(() -> {
      crud().delete(TABLE, ids);
      String in = Values.placeholders(ids.size());
      Db.update("update def_gen_table_column set is_del=1 where table_id in (" + in + ")", ids.toArray());
      return true;
    });
  }

  public Kv get(Long id) {
    Kv vo = crud().get(TABLE, id);
    return vo == null ? null : GenEnums.toApi(vo);
  }

  public List<Kv> query(Map<String, Object> model) {
    return apiList(crud().query(TABLE, GenEnums.toDb(model)));
  }

  public List<Kv> findByIds(Collection<Long> ids) {
    return apiList(crud().findByIds(TABLE, ids));
  }

  public IPageResp<Kv> page(Map<String, Object> params) {
    IPageResp<Kv> page = crud().page(TABLE, params);
    apiList(page.getRecords());
    return page;
  }

  private List<Kv> apiList(List<Kv> list) {
    for (Kv vo : list) {
      GenEnums.toApi(vo);
    }
    return list;
  }
}
