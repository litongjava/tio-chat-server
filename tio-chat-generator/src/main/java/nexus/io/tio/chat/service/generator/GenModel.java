package nexus.io.tio.chat.service.generator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.service.base.crud.TableMeta;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 组装模板参数。模板中 table 和字段使用驼峰属性名，枚举列使用数据库编码
 */
final class GenModel {
  private static final Set<String> SUPER_ENTITY = Set.of("id", "create_time", "create_by");
  private static final Set<String> ENTITY = Set.of("id", "create_time", "create_by", "update_time", "update_by");
  private static final Set<String> TREE = Set.of("id", "create_time", "create_by", "update_time", "update_by", "parent_id", "sort_value");
  private static final String PACKAGE = "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*";
  private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /** 模板通过 uidGenerator.getUid() 生成初始化 SQL 中的主键 */
  public static class UidGenerator {
    public long getUid() {
      return SnowflakeIdUtils.id();
    }
  }

  private GenModel() {
  }

  static String basePackage(Kv table) {
    String module = table.getStr("moduleName");
    return table.getStr("parent") + (module == null || module.isEmpty() ? "" : "." + module);
  }

  static String childPath(Kv table) {
    String child = table.getStr("childPackageName");
    return child == null || child.isEmpty() ? "" : "/" + child.replace('.', '/');
  }

  private static String childPackage(Kv table) {
    String child = table.getStr("childPackageName");
    return child == null || child.isEmpty() ? "" : "." + child;
  }

  static Kv loadTable(Long id) {
    Kv row = Db.findFirstMap("select * from def_gen_table where id=? and is_del=0", id);
    ChatErrors.require(row != null, "表不存在，请先导入表");
    Kv table = camel("def_gen_table", row);
    String name = table.getStr("name");
    require(table, "serviceName", name, "服务名");
    require(table, "moduleName", name, "模块名");
    require(table, "plusApplicationName", name, "前端应用名");
    require(table, "plusModuleName", name, "前端模块名");
    checkName(table.getStr("entityName"), "[A-Za-z_][A-Za-z0-9_]*", name, "实体名");
    checkName(table.getStr("parent"), PACKAGE, name, "基础包");
    checkName(table.getStr("moduleName"), PACKAGE, name, "模块名");
    checkName(table.getStr("childPackageName"), PACKAGE, name, "子包名");
    checkName(table.getStr("serviceName"), "[A-Za-z0-9_-]+", name, "服务名");
    ChatErrors.require(table.get("menuApplicationId") != null, "请先配置并保存【" + name + "】表，生成信息页面的  [菜单所属应用]");
    if (GenEnums.TPL_MAIN_SUB.equals(table.getStr("tplType"))) {
      ChatErrors.require(table.get("subId") != null, "请先配置并保存【" + name + "】表，生成信息页面的  [从表]");
      ChatErrors.require(table.get("subJavaFieldName") != null, "请先配置并保存【" + name + "】表，生成信息页面的  [从表字段]");
    }
    return table;
  }

  /** 这些配置会成为包名和文件路径 */
  private static void checkName(String value, String pattern, String name, String label) {
    if (value != null && !value.isEmpty()) {
      ChatErrors.require(value.matches(pattern), "【" + name + "】表的 [" + label + "] 不合法：" + value);
    }
  }

  private static void require(Kv table, String field, String name, String label) {
    String value = table.getStr(field);
    ChatErrors.require(value != null && !value.isEmpty(), "请先配置并保存【" + name + "】表，生成信息页面的 [" + label + "]");
  }

  private static Kv camel(String tableName, Kv row) {
    Kv result = Kv.create();
    for (Map.Entry<String, String> field : TableMeta.fields(tableName).entrySet()) {
      Object value = row.get(field.getValue());
      String name = field.getKey();
      if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2)) || name.endsWith("Show")) {
        // 模板直接把这些列当作布尔值判断，未设置时按 false
        value = isTrue(value);
      }
      if (value != null) {
        result.set(name, value);
      }
    }
    return result;
  }

  private static boolean isTrue(Object value) {
    if (value instanceof byte[] bytes) {
      return bytes.length > 0 && bytes[0] != 0;
    }
    if (value instanceof Boolean flag) {
      return flag;
    }
    return value instanceof Number number && number.intValue() != 0;
  }

  static Map<String, Object> build(Kv table, Map<String, Object> sub) {
    List<Kv> allFields = new ArrayList<>();
    String sql = "select * from def_gen_table_column where table_id=? and is_del=0 order by sort_value, id";
    for (Kv row : Db.findMaps(sql, table.getLong("id"))) {
      allFields.add(camel("def_gen_table_column", row));
    }
    List<Kv> pk = new ArrayList<>();
    List<Kv> common = new ArrayList<>();
    List<Kv> fields = new ArrayList<>();
    Set<String> superColumns = superColumns(table.getStr("entitySuperClass"));
    for (Kv field : allFields) {
      if (Boolean.TRUE.equals(field.get("isPk"))) {
        pk.add(field);
      }
      (superColumns.contains(field.getStr("name")) ? common : fields).add(field);
    }
    ChatErrors.require(!pk.isEmpty(), "请设置主键id");
    ChatErrors.require(pk.size() == 1, "目前只支持1个主键id, 不支持符合组件");

    if (table.get("popupType") == null) {
      table.set("popupType", GenEnums.POPUP_MODAL);
    }
    if (table.get("superClass") == null) {
      table.set("superClass", GenEnums.SUPER_CLASS);
    }
    String entity = table.getStr("entityName");
    String base = basePackage(table);
    String child = childPackage(table);
    Map<String, Object> map = new HashMap<>();
    map.put("sub", sub == null ? Map.of() : sub);
    map.put("table", table);
    map.put("datetime", LocalDateTime.now().format(DATETIME));
    map.put("dbType", GenDbMeta.dbType(table.getLong("dsId")));
    map.put("oracle", "oracle");
    map.put("isTreeEntity", GenEnums.ENTITY_TREE.equals(table.getStr("entitySuperClass")));
    map.put("pkField", pk.get(0));
    map.put("allFields", allFields);
    map.put("commonFields", common);
    map.put("fields", fields);
    map.put("tenantIdColumn", "tenant_id");
    map.put("projectPrefix", GenConfig.projectPrefix());
    map.put("author", table.getStr("author") == null ? "" : table.getStr("author"));
    map.put("parent", table.getStr("parent"));
    map.put("utilParent", table.getStr("parent"));
    map.put("moduleName", table.getStr("moduleName"));
    map.put("mappingHyphen", GenTemplates.uncapitalize(entity));
    map.put("controllerConfig", Map.of("hyphenStyle", false));
    map.put("dataTsImport", dataTsImports(allFields));
    map.put("controllerName", entity + "RouteConfig");
    map.put("saveVoName", entity + "SaveVO");
    map.put("updateVoName", entity + "UpdateVO");
    map.put("resultVoName", entity + "ResultVO");
    map.put("pageQueryName", entity + "PageQuery");

    map.put("modelPackage", base + ".model" + child);
    map.put("servicePackage", base + ".service" + child);
    map.put("handlerPackage", base + ".handler" + child);
    map.put("routePackage", base + ".config.route");
    map.put("enumPackage", base + ".enums" + child);
    map.put("routePath", "/api/" + table.getStr("serviceName") + "/" + GenTemplates.uncapitalize(entity));
    map.put("modelFields", modelFields(allFields));
    map.put("imports", imports(allFields, GenEnums.TPL_MAIN_SUB.equals(table.getStr("tplType"))));
    map.put("logicDelete", allFields.stream().anyMatch(field -> Boolean.TRUE.equals(field.get("isLogicDeleteField"))));
    map.put("snowflakeId", "Long".equals(pk.get(0).getStr("javaType")) && !Boolean.TRUE.equals(pk.get(0).get("isIncrement")));
    putOptions(map, table, sub, allFields);
    map.put("hasCreateTime", hasField(allFields, "create_time"));
    map.put("hasUpdateTime", hasField(allFields, "update_time"));

    map.put("TPL_TREE", GenEnums.TPL_TREE);
    map.put("TPL_MAIN_SUB", GenEnums.TPL_MAIN_SUB);
    map.put("POPUP_TYPE_MODAL", GenEnums.POPUP_MODAL);
    map.put("POPUP_TYPE_DRAWER", GenEnums.POPUP_DRAWER);
    map.put("POPUP_TYPE_JUMP", GenEnums.POPUP_JUMP);
    map.put("SUPER_CLASS_SUPER_POI_CLASS", GenEnums.SUPER_POI_CLASS);
    map.putAll(sqlParams(table));

    Map<String, Kv> enums = new LinkedHashMap<>();
    List<Kv> dicts = new ArrayList<>();
    for (Kv field : allFields) {
      Kv enumType = GenColumns.enumType(entity, field.getStr("javaField"), field.getStr("comment"));
      if (enumType != null) {
        enums.put(enumType.getStr("enumName"), enumType);
      }
      Kv dict = GenColumns.dict(field.getStr("dictType"), field.getStr("swaggerComment"), field.getStr("comment"));
      if (dict != null) {
        dicts.add(dict);
      }
    }
    map.put("enumList", new ArrayList<>(enums.values()));
    map.put("dictList", dicts);
    List<Kv> echoFields = echoFields(entity, allFields);
    map.put("echoFields", echoFields);
    map.put("hasEcho", !echoFields.isEmpty() && Boolean.TRUE.equals(map.get("hasService")));
    return map;
  }

  /** 表配置上影响生成结果的选项：父类决定有哪些接口，其余决定数据源、租户、树和主从 */
  private static void putOptions(Map<String, Object> map, Kv table, Map<String, Object> sub, List<Kv> allFields) {
    String superClass = table.getStr("superClass");
    boolean hasService = !GenEnums.SUPER_NONE_CS.equals(superClass) && !GenEnums.SUPER_NONE.equals(superClass);
    boolean simple = GenEnums.SUPER_SIMPLE_CLASS.equals(superClass);
    boolean canRead = hasService && !simple && !GenEnums.SUPER_WRITE_CLASS.equals(superClass);
    boolean canWrite = hasService && !simple && !GenEnums.SUPER_READ_CLASS.equals(superClass);
    map.put("hasService", hasService);
    map.put("canRead", canRead);
    map.put("canWrite", canWrite);
    map.put("canExport", GenEnums.SUPER_POI_CLASS.equals(superClass));
    map.put("canCache", GenEnums.SUPER_CACHE_CLASS.equals(superClass));

    boolean tree = GenEnums.ENTITY_TREE.equals(table.getStr("entitySuperClass"));
    if (tree) {
      ChatErrors.require(hasField(allFields, "parent_id"), "树结构的【" + table.getStr("name") + "】表需要 parent_id 字段");
    }
    map.put("hasTree", tree && hasService);
    map.put("hasSortValue", hasField(allFields, "sort_value"));
    map.put("tenantLine", hasField(allFields, "tenant_id") && !Boolean.TRUE.equals(table.get("isTenantLine")));
    map.put("dbCall", dbCall(table));

    boolean mainSub = GenEnums.TPL_MAIN_SUB.equals(table.getStr("tplType")) && sub != null;
    map.put("mainSub", mainSub && hasService);
    if (mainSub) {
      putSubForeignKey(map, table, sub);
    }
  }

  @SuppressWarnings("unchecked")
  private static void putSubForeignKey(Map<String, Object> map, Kv table, Map<String, Object> sub) {
    String field = table.getStr("subJavaFieldName");
    for (Kv column : (List<Kv>) sub.get("allFields")) {
      if (field.equals(column.getStr("javaField"))) {
        map.put("subFkColumn", column.getStr("name"));
        map.put("subFkType", column.getStr("javaType"));
        return;
      }
    }
    throw ChatErrors.of("从表中没有字段：" + field);
  }

  /** 使用指定的数据源时生成 Db.use("名称")，名称会写进代码，这里只允许普通字符 */
  private static String dbCall(Kv table) {
    String value = table.getStr("dsValue");
    if (!Boolean.TRUE.equals(table.get("isDs")) || value == null || value.isBlank()) {
      return "Db.use()";
    }
    String name = value.replace("\"", "").trim();
    ChatErrors.require(name.matches("[A-Za-z0-9_.-]+"), "【" + table.getStr("name") + "】表的 [数据源] 不合法：" + value);
    return "Db.use(\"" + name + "\")";
  }

  /** 需要回显的字段：注释中声明了枚举或字典，字典类型同时生成常量 */
  private static List<Kv> echoFields(String entity, List<Kv> allFields) {
    List<Kv> result = new ArrayList<>();
    for (Kv field : allFields) {
      if (Boolean.TRUE.equals(field.get("isLogicDeleteField")) || "tenant_id".equals(field.getStr("name"))) {
        continue;
      }
      String comment = field.getStr("swaggerComment") == null ? field.getStr("name") : field.getStr("swaggerComment");
      Kv enumType = GenColumns.enumType(entity, field.getStr("javaField"), field.getStr("comment"));
      String dictType = field.getStr("dictType");
      if (enumType != null) {
        result.add(Kv.by("javaField", field.getStr("javaField")).set("comment", comment).set("enumName", enumType.getStr("enumName")));
      } else if (dictType != null && !dictType.isEmpty()) {
        String key = dictType.substring(dictType.lastIndexOf('.') + 1);
        result.add(Kv.by("javaField", field.getStr("javaField")).set("comment", comment).set("dictType", key)
            .set("constant", GenColumns.constantName(field.getStr("name"))));
      }
    }
    return result;
  }

  /** 前端 data.tsx 根据字段类型需要导入的内容 */
  private static Set<String> dataTsImports(List<Kv> allFields) {
    String common = "/@/utils/" + GenConfig.projectPrefix() + "/common'";
    Set<String> imports = new TreeSet<>();
    for (Kv field : allFields) {
      String javaType = field.getStr("javaType");
      if ("LocalDateTime".equals(javaType) || "LocalDate".equals(javaType)) {
        imports.add("{ dateUtil } from '/@/utils/dateUtil'");
      }
      if ("Boolean".equals(javaType)) {
        imports.add("{ yesNoComponentProps } from '" + common);
      }
      String enumStr = field.getStr("enumStr");
      if (enumStr != null && !enumStr.isEmpty()) {
        imports.add("{ enumComponentProps } from '" + common);
        imports.add("{ EnumEnum } from '/@/enums/commonEnum'");
      }
      String dictType = field.getStr("dictType");
      if (dictType != null && !dictType.isEmpty() && !dictType.contains("\"")) {
        imports.add("{ DictEnum } from '/@/enums/commonEnum'");
        imports.add("{ dictComponentProps } from '" + common);
      }
    }
    return imports;
  }

  private static boolean hasField(List<Kv> fields, String column) {
    return fields.stream().anyMatch(field -> column.equals(field.getStr("name")));
  }

  private static Set<String> superColumns(String entitySuperClass) {
    if (GenEnums.ENTITY_SUPER_ENTITY.equals(entitySuperClass)) {
      return SUPER_ENTITY;
    }
    if (GenEnums.ENTITY_ENTITY.equals(entitySuperClass)) {
      return ENTITY;
    }
    return GenEnums.ENTITY_TREE.equals(entitySuperClass) ? TREE : Set.of();
  }

  /** 实体类的字段：枚举列在实体中按编码保存为 String */
  private static List<Kv> modelFields(List<Kv> allFields) {
    List<Kv> result = new ArrayList<>();
    for (Kv field : allFields) {
      if (Boolean.TRUE.equals(field.get("isLogicDeleteField")) || "tenant_id".equals(field.getStr("name"))) {
        continue;
      }
      Kv copy = Kv.create();
      copy.putAll(field);
      String enumStr = field.getStr("enumStr");
      copy.set("modelType", enumStr == null || enumStr.isEmpty() ? field.getStr("javaType") : "String");
      copy.set("condition", condition(field.getStr("queryType")));
      result.add(copy);
    }
    return result;
  }

  /** 查询方式可能是枚举名称，也可能是数据库中的编码 */
  private static String condition(String queryType) {
    return switch (queryType == null ? "" : queryType) {
    case "LIKE", "03", "ORACLE_LIKE", "04" -> "LIKE";
    case "LIKE_LEFT", "05" -> "LIKE_LEFT";
    case "LIKE_RIGHT", "06" -> "LIKE_RIGHT";
    case "NOT_EQUAL", "02" -> "NOT_EQUAL";
    default -> "EQUAL";
    };
  }

  private static Set<String> imports(List<Kv> allFields, boolean mainSub) {
    Set<String> imports = new LinkedHashSet<>();
    if (mainSub) {
      imports.add("java.util.List");
    }
    for (Kv field : allFields) {
      switch (String.valueOf(field.getStr("javaType"))) {
      case "BigDecimal" -> imports.add("java.math.BigDecimal");
      case "LocalDate" -> imports.add("java.time.LocalDate");
      case "LocalTime" -> imports.add("java.time.LocalTime");
      case "LocalDateTime" -> imports.add("java.time.LocalDateTime");
      default -> {
      }
      }
    }
    return imports;
  }

  private static Map<String, Object> sqlParams(Kv table) {
    Map<String, Object> map = new HashMap<>();
    LoginSession session = LoginContext.get();
    map.put("applicationId", table.get("menuApplicationId"));
    map.put("parentMenuId", table.get("menuParentId") == null ? 0L : table.get("menuParentId"));
    map.put("createdBy", session == null ? 0L : session.getUserId());
    for (String name : List.of("menuId", "buttonAddId", "buttonEditId", "buttonCopyId", "buttonDeleteId", "buttonViewId",
        "apiPageId", "apiDetailId", "apiAddId", "apiEditId", "apiDeleteId", "apiCopyId", "trMenuId", "trButtonAddId",
        "trButtonEditId", "trButtonCopyId", "trButtonDeleteId", "trButtonViewId")) {
      map.put(name, SnowflakeIdUtils.id());
    }
    map.put("uidGenerator", new UidGenerator());
    return map;
  }
}
