package nexus.io.tio.chat.service.generator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.tio.chat.utils.ChatErrors;

/**
 * 每种生成目标用到的模板、生成的文件路径和默认的覆盖策略
 */
final class GenTemplates {
  static final String BACKEND = "BACKEND";
  static final String WEB_PLUS = "WEB_PLUS";
  static final String WEB_SOYBEAN = "WEB_SOYBEAN";
  static final String WEB_VBEN5 = "WEB_VBEN5";

  static final String OVERRIDE = "OVERRIDE";
  static final String EXIST_IGNORE = "EXIST_IGNORE";
  static final String ADD = "ADD";
  static final String IGNORE = "IGNORE";

  private static final String JAVA = "/templates/backend/java/";
  static final String ENUM = JAVA + "enum.java.ftl";
  static final String ECHO = JAVA + "echo.java.ftl";
  private static final String SQL = "/templates/backend/sql/menu.sql.ftl";
  private static final String VBEN = "/templates/web/vben/";
  private static final String SOY = "/templates/web/soybean/";
  private static final String VBEN5 = "/templates/web/vben5/";

  /** 字段名 -> 模板 */
  private static final Map<String, String> BACKEND_FIELDS = new LinkedHashMap<>();
  private static final Map<String, String> WEB_FIELDS = new LinkedHashMap<>();

  static {
    BACKEND_FIELDS.put("enum", ENUM);
    BACKEND_FIELDS.put("sql", SQL);
    BACKEND_FIELDS.put("entity", JAVA + "entity.java.ftl");
    BACKEND_FIELDS.put("service", JAVA + "service.java.ftl");
    BACKEND_FIELDS.put("pageHandler", JAVA + "pageHandler.java.ftl");
    BACKEND_FIELDS.put("detailHandler", JAVA + "detailHandler.java.ftl");
    BACKEND_FIELDS.put("saveHandler", JAVA + "saveHandler.java.ftl");
    BACKEND_FIELDS.put("updateHandler", JAVA + "updateHandler.java.ftl");
    BACKEND_FIELDS.put("deleteHandler", JAVA + "deleteHandler.java.ftl");
    BACKEND_FIELDS.put("queryHandler", JAVA + "queryHandler.java.ftl");
    BACKEND_FIELDS.put("getHandler", JAVA + "getHandler.java.ftl");
    BACKEND_FIELDS.put("findByIdsHandler", JAVA + "findByIdsHandler.java.ftl");
    BACKEND_FIELDS.put("copyHandler", JAVA + "copyHandler.java.ftl");
    BACKEND_FIELDS.put("treeHandler", JAVA + "treeHandler.java.ftl");
    BACKEND_FIELDS.put("exportHandler", JAVA + "exportHandler.java.ftl");
    BACKEND_FIELDS.put("cacheHandler", JAVA + "cacheHandler.java.ftl");
    BACKEND_FIELDS.put("echo", ECHO);
    BACKEND_FIELDS.put("routeConfig", JAVA + "routeConfig.java.ftl");
    WEB_FIELDS.put("api", VBEN + "simple/typescript/api.ts.ftl");
    WEB_FIELDS.put("model", VBEN + "simple/typescript/model.ts.ftl");
    WEB_FIELDS.put("langEn", VBEN + "simple/typescript/langEn.ts.ftl");
    WEB_FIELDS.put("langZh", VBEN + "simple/typescript/langZh.ts.ftl");
    WEB_FIELDS.put("data", VBEN + "simple/typescript/data.tsx.ftl");
    WEB_FIELDS.put("index", VBEN + "simple/vue/index.vue.ftl");
    WEB_FIELDS.put("edit", VBEN + "simple/vue/edit.vue.ftl");
    WEB_FIELDS.put("jumpEdit", VBEN + "simple/vue/jumpEdit.vue.ftl");
    WEB_FIELDS.put("treeIndex", VBEN + "tree/vue/index.vue.ftl");
    WEB_FIELDS.put("treeEdit", VBEN + "tree/vue/edit.vue.ftl");
    WEB_FIELDS.put("treeTree", VBEN + "tree/vue/tree.vue.ftl");
  }

  private GenTemplates() {
  }

  static Map<String, String> fieldTemplates() {
    Map<String, String> result = new LinkedHashMap<>(BACKEND_FIELDS);
    result.putAll(WEB_FIELDS);
    return result;
  }

  static Map<String, String> defaultStrategies() {
    Map<String, String> result = new LinkedHashMap<>();
    fieldTemplates().forEach((field, template) -> result.put(field, strategy(template)));
    return result;
  }

  /** 实体、SQL、接口定义和语言包由表结构决定，默认覆盖；业务代码和页面可能被修改过，存在时跳过 */
  static String strategy(String template) {
    boolean generated = template.equals(ENUM) || template.equals(ECHO) || template.equals(SQL) || template.endsWith("entity.java.ftl")
        || template.endsWith("api.ts.ftl") || template.endsWith("model.ts.ftl") || template.contains("lang");
    return generated ? OVERRIDE : EXIST_IGNORE;
  }

  /** 后端文件由表配置决定：父类决定有哪些接口，树结构多一个 tree 接口，有枚举或字典时生成回显类 */
  private static List<String> backend(Map<String, Object> model) {
    List<String> list = new ArrayList<>(List.of(SQL, JAVA + "entity.java.ftl", JAVA + "service.java.ftl", JAVA + "routeConfig.java.ftl"));
    if (isOn(model, "canRead")) {
      addAll(list, JAVA, "pageHandler.java.ftl", "queryHandler.java.ftl", "findByIdsHandler.java.ftl", "detailHandler.java.ftl", "getHandler.java.ftl");
    }
    if (isOn(model, "canWrite")) {
      addAll(list, JAVA, "saveHandler.java.ftl", "copyHandler.java.ftl", "updateHandler.java.ftl", "deleteHandler.java.ftl");
    }
    if (isOn(model, "canCache")) {
      list.add(JAVA + "cacheHandler.java.ftl");
    }
    if (isOn(model, "canExport")) {
      list.add(JAVA + "exportHandler.java.ftl");
    }
    if (isOn(model, "hasTree")) {
      list.add(JAVA + "treeHandler.java.ftl");
    }
    if (isOn(model, "hasEcho")) {
      list.add(ECHO);
    }
    return list;
  }

  private static boolean isOn(Map<String, Object> model, String key) {
    return Boolean.TRUE.equals(model.get(key));
  }

  static List<String> list(String template, Map<String, Object> model) {
    Kv table = (Kv) model.get("table");
    return list(template, table.getStr("tplType"), table.getStr("popupType"), model);
  }

  private static List<String> list(String template, String tplType, String popupType, Map<String, Object> model) {
    List<String> list = new ArrayList<>();
    boolean tree = GenEnums.TPL_TREE.equals(tplType);
    boolean mainSub = GenEnums.TPL_MAIN_SUB.equals(tplType);
    boolean jump = GenEnums.POPUP_JUMP.equals(popupType);
    switch (template) {
    case BACKEND -> list.addAll(backend(model));
    case WEB_PLUS -> {
      addAll(list, VBEN, "simple/typescript/api.ts.ftl", "simple/typescript/model.ts.ftl", "simple/typescript/langEn.ts.ftl",
          "simple/typescript/langZh.ts.ftl", "simple/typescript/data.tsx.ftl");
      if (tree) {
        addAll(list, VBEN, "tree/vue/index.vue.ftl", "tree/vue/edit.vue.ftl", "tree/vue/tree.vue.ftl");
      } else if (mainSub) {
        addAll(list, VBEN, jump ? "mainSub/vue/jumpEdit.vue.ftl" : "mainSub/vue/edit.vue.ftl", "mainSub/vue/index.vue.ftl",
            "mainSub/vue/subIndex.vue.ftl", "mainSub/typescript/subData.tsx.ftl");
      } else {
        addAll(list, VBEN, jump ? "simple/vue/jumpEdit.vue.ftl" : "simple/vue/edit.vue.ftl", "simple/vue/index.vue.ftl");
      }
    }
    case WEB_SOYBEAN, WEB_VBEN5 -> {
      String root = WEB_SOYBEAN.equals(template) ? SOY : VBEN5;
      addAll(list, root, "simple/typescript/api.ts.ftl", "simple/typescript/model.ts.ftl", "simple/typescript/langEn.ts.ftl",
          "simple/typescript/langZh.ts.ftl");
      if (tree) {
        addAll(list, root, "tree/vue/index.vue.ftl", "tree/vue/edit.vue.ftl", "tree/vue/tree.vue.ftl", "tree/typescript/crud.tsx.ftl");
      } else if (!mainSub) {
        if (jump) {
          addAll(list, root, "simple/vue/edit.vue.ftl");
        }
        addAll(list, root, "simple/vue/index.vue.ftl", "simple/typescript/crud.tsx.ftl");
      }
    }
    default -> throw ChatErrors.of("请选择需生成类型");
    }
    return list;
  }

  /** 主从结构的从表：后端代码，以及主表页面用到的从表接口定义和语言包 */
  static List<String> subList(String template, Map<String, Object> subModel) {
    if (BACKEND.equals(template)) {
      return backend(subModel);
    }
    List<String> list = new ArrayList<>();
    if (WEB_PLUS.equals(template)) {
      addAll(list, VBEN, "simple/typescript/api.ts.ftl", "simple/typescript/model.ts.ftl", "simple/typescript/langEn.ts.ftl", "simple/typescript/langZh.ts.ftl");
    }
    return list;
  }

  private static void addAll(List<String> list, String root, String... files) {
    for (String file : files) {
      list.add(root + file);
    }
  }

  static String outputDir(String template, Kv table) {
    return switch (template) {
    case BACKEND -> table.getStr("outputDir");
    case WEB_SOYBEAN -> table.getStr("frontSoyOutputDir");
    case WEB_VBEN5 -> table.getStr("frontVben5OutputDir");
    default -> table.getStr("frontOutputDir");
    };
  }

  /** 相对于输出目录的文件路径；同时也是压缩包内的路径 */
  static String outputFile(String templateType, String template, Kv table, Kv subTable, String enumName) {
    String entity = table.getStr("entityName");
    String lower = uncapitalize(entity);
    if (BACKEND.equals(templateType)) {
      return backendFile(template, table, entity, enumName);
    }
    String module = table.getStr("plusApplicationName") + "/" + table.getStr("plusModuleName");
    String views = "src/views/" + module + "/" + lower;
    String name = template.substring(template.lastIndexOf('/') + 1).replace(".ftl", "");
    boolean plus = WEB_PLUS.equals(templateType);
    boolean soy = WEB_SOYBEAN.equals(templateType);
    String api = (soy ? "src/service/fetch/" : "src/api/") + module;
    String lang = plus ? "src/locales/lang/" : "src/locales/langs/";
    String langExt = WEB_VBEN5.equals(templateType) ? ".json" : ".ts";
    switch (name) {
    case "api.ts":
      return api + "/" + lower + ".ts";
    case "model.ts":
      return api + "/model/" + lower + "Model.ts";
    case "langEn.ts":
      return lang + (plus ? "en/" : "en-US/") + module + "/" + lower + langExt;
    case "langZh.ts":
      return lang + "zh-CN/" + module + "/" + lower + langExt;
    case "data.tsx":
      return views + "/" + lower + ".data.tsx";
    case "crud.tsx":
      return views + "/data/crud.tsx";
    case "index.vue":
      return views + "/index.vue";
    case "edit.vue", "jumpEdit.vue":
      if (plus) {
        return views + "/Edit.vue";
      }
      return views + (soy ? "/modules/Edit.vue" : "/modules/edit.vue");
    case "tree.vue":
      if (plus) {
        return views + "/Tree.vue";
      }
      return views + (soy ? "/modules/Tree.vue" : "/modules/tree.vue");
    case "subIndex.vue":
      return views + "/" + uncapitalize(subTable.getStr("entityName")) + "/index.vue";
    case "subData.tsx":
      String sub = uncapitalize(subTable.getStr("entityName"));
      return views + "/" + sub + "/" + sub + ".data.tsx";
    default:
      throw ChatErrors.of("未知模板：" + template);
    }
  }

  private static String backendFile(String template, Kv table, String entity, String enumName) {
    String service = table.getStr("serviceName");
    String root = service + "/src/main/java/" + GenModel.basePackage(table).replace('.', '/') + "/";
    String child = GenModel.childPath(table);
    String name = template.substring(template.lastIndexOf('/') + 1);
    return switch (name) {
    case "menu.sql.ftl" -> service + "/src/main/resources/sql/" + entity + "Menu.sql";
    case "enum.java.ftl" -> root + "enums" + child + "/" + enumName + ".java";
    case "echo.java.ftl" -> root + "enums" + child + "/" + entity + "Echo.java";
    case "entity.java.ftl" -> root + "model" + child + "/" + entity + ".java";
    case "service.java.ftl" -> root + "service" + child + "/" + entity + "Service.java";
    case "routeConfig.java.ftl" -> root + "config/route/" + entity + "RouteConfig.java";
    case "pageHandler.java.ftl" -> root + "handler" + child + "/" + entity + "PageHandler.java";
    case "detailHandler.java.ftl" -> root + "handler" + child + "/" + entity + "DetailHandler.java";
    case "saveHandler.java.ftl" -> root + "handler" + child + "/" + entity + "SaveHandler.java";
    case "updateHandler.java.ftl" -> root + "handler" + child + "/" + entity + "UpdateHandler.java";
    case "deleteHandler.java.ftl" -> root + "handler" + child + "/" + entity + "DeleteHandler.java";
    case "queryHandler.java.ftl" -> root + "handler" + child + "/" + entity + "QueryHandler.java";
    case "getHandler.java.ftl" -> root + "handler" + child + "/" + entity + "GetHandler.java";
    case "findByIdsHandler.java.ftl" -> root + "handler" + child + "/" + entity + "FindByIdsHandler.java";
    case "copyHandler.java.ftl" -> root + "handler" + child + "/" + entity + "CopyHandler.java";
    case "treeHandler.java.ftl" -> root + "handler" + child + "/" + entity + "TreeHandler.java";
    case "exportHandler.java.ftl" -> root + "handler" + child + "/" + entity + "ExportHandler.java";
    case "cacheHandler.java.ftl" -> root + "handler" + child + "/" + entity + "CacheHandler.java";
    default -> throw ChatErrors.of("未知模板：" + template);
    };
  }

  static String uncapitalize(String text) {
    return text == null || text.isEmpty() ? "" : Character.toLowerCase(text.charAt(0)) + text.substring(1);
  }
}
