package nexus.io.tio.chat.service.generator;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Row;

/**
 * 把数据库字段转换成代码生成的字段配置：Java 与 TS 类型、三套前端组件、查询方式，并解析注释中的枚举和字典
 */
final class GenColumns {
  private static final Set<String> AUDIT = Set.of("create_by", "create_time", "update_by", "update_time", "created_org_id");
  private static final String LOGIC_DELETE = "is_del";
  private static final Pattern ENUM = Pattern.compile("#([a-zA-Z\\d\"._]+)?\\{(.*?)}");
  private static final Pattern ENUM_ITEM = Pattern.compile("([A-Za-z1-9_-]+):(.*?);");
  private static final Pattern ECHO = Pattern.compile("@Echo\\(.*?\\)");
  private static final Pattern ECHO_DICT = Pattern.compile("dictType *= *\"?([\\w.]+)\"?");
  private static final Pattern DICT_ITEMS = Pattern.compile("\\[(.*?)]");

  private GenColumns() {
  }

  /** 字段类型：Java 类型、TS 类型、vben 组件、vxe 组件、soybean 组件 */
  private static String[] type(String columnType) {
    String t = GenDbMeta.normalizeType(columnType);
    if (t.contains("char") || t.contains("text") || t.contains("json") || t.contains("enum")) {
      return new String[] { "String", "string", "Input", "$input", "text" };
    }
    if (t.contains("bigint")) {
      return new String[] { "Long", "string", "Input", "$input", "text" };
    }
    if (t.contains("tinyint(1)") || t.contains("bit")) {
      return new String[] { "Boolean", "boolean", "RadioGroup", "$radio", "dict-radio" };
    }
    if (t.contains("int")) {
      return new String[] { "Integer", "number", "Input", "$input", "text" };
    }
    if (t.contains("decimal")) {
      return new String[] { "BigDecimal", "string", "Input", "$input", "number" };
    }
    if (t.contains("blob") || t.contains("binary")) {
      return new String[] { "byte[]", "string", "Input", "$input", "text" };
    }
    if (t.contains("float")) {
      return new String[] { "Float", "number", "Input", "$input", "text" };
    }
    if (t.contains("double")) {
      return new String[] { "Double", "number", "Input", "$input", "text" };
    }
    if (t.startsWith("datetime") || t.startsWith("timestamp")) {
      return new String[] { "LocalDateTime", "string", "DatePicker", "$input", "datetime" };
    }
    if (t.startsWith("date")) {
      return new String[] { "LocalDate", "string", "DatePicker", "$input", "date" };
    }
    if (t.startsWith("time")) {
      return new String[] { "LocalTime", "string", "TimePicker", "$input", "time" };
    }
    if (t.startsWith("year")) {
      return new String[] { "Integer", "string", "DatePicker", "$input", "date" };
    }
    return new String[] { "String", "string", "Input", "$input", "text" };
  }

  static String swaggerComment(String comment) {
    if (comment == null) {
      return "";
    }
    String text = comment.trim();
    int semicolon = text.indexOf(';');
    return (semicolon >= 0 ? text.substring(0, semicolon) : text).replace("\n", " ");
  }

  static String javaField(String column) {
    StringBuilder sb = new StringBuilder();
    boolean upper = false;
    for (char c : (column.endsWith("_") ? column.substring(0, column.length() - 1) : column).toCharArray()) {
      if (c == '_') {
        upper = sb.length() > 0;
      } else {
        sb.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return sb.toString();
  }

  static String constantName(String column) {
    return column.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
  }

  static String capitalize(String text) {
    return text == null || text.isEmpty() ? "" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  /** 新字段的配置。id 和 table_id 由调用方设置 */
  static Row init(Kv table, Kv column) {
    String name = column.getStr("name");
    String comment = column.getStr("comment");
    String javaField = javaField(name);
    boolean logicDelete = LOGIC_DELETE.equals(name);
    boolean pk = Boolean.TRUE.equals(column.get("pk"));
    Row row = new Row();
    row.set("name", name).set("comment_", comment).set("swagger_comment", swaggerComment(comment)).set("java_field", javaField)
        .set("type", column.getStr("dataType").toUpperCase(Locale.ROOT)).set("size_", column.get("size") == null ? 0L : column.get("size")).set("is_pk", pk)
        .set("is_required", !Boolean.TRUE.equals(column.get("nullable")))
        .set("is_increment", Boolean.TRUE.equals(column.get("autoIncrement"))).set("is_version_field", false)
        .set("is_logic_delete_field", logicDelete).set("edit_def_value", defaultValue(column.getStr("columnDef")));
    boolean managed = pk || logicDelete || AUDIT.contains(name) || "tenant_id".equals(name);
    row.set("is_edit", !managed).set("is_list", !managed).set("is_query", !managed);
    if (name.equals("create_by") || name.equals("create_time")) {
      row.set("fill", "INSERT");
    } else if (name.equals("update_by") || name.equals("update_time")) {
      row.set("fill", "INSERT_UPDATE");
    }

    Matcher echo = comment == null ? null : ECHO.matcher(comment);
    String echoStr = echo != null && echo.find() ? echo.group() : null;
    if (echoStr != null) {
      Matcher dict = ECHO_DICT.matcher(echoStr);
      row.set("echo_str", echoStr).set("dict_type", dict.find() ? dict.group(1) : null);
    }
    Kv enumType = enumType(table.getStr("entity_name"), javaField, comment);
    if (enumType != null) {
      return row.set("enum_str", enumType.getStr("enumStr")).set("java_type", enumType.getStr("enumName"))
          .set("echo_str", "@Echo(api = Echo.ENUM_API)").set("ts_type", "string").set("component", "ApiRadioGroup")
          .set("soy_component", "dict-radio").set("vxe_component", "$radio").set("query_type", "EQUAL");
    }
    String[] type = type(column.getStr("columnType"));
    String component = type[2];
    String vxe = type[3];
    String soy = type[4];
    String lower = javaField.toLowerCase(Locale.ROOT);
    if (lower.contains("password")) {
      component = "InputPassword";
      soy = "password";
    } else if (lower.contains("remark")) {
      component = "InputTextArea";
      vxe = "$textarea";
      soy = "textarea";
    }
    if (echoStr != null) {
      component = "ApiRadioGroup";
      vxe = "$radio";
      soy = "dict-radio";
    }
    return row.set("java_type", type[0]).set("ts_type", type[1]).set("query_type", "String".equals(type[0]) ? "LIKE" : "EQUAL")
        .set("component", component).set("vxe_component", vxe).set("soy_component", soy);
  }

  private static String defaultValue(String columnDef) {
    if (columnDef == null || columnDef.isEmpty()) {
      return "";
    }
    return switch (columnDef) {
    case "b'0'" -> "false";
    case "b'1'" -> "true";
    default -> columnDef;
    };
  }

  /**
   * 解析注释中的枚举：说明 #枚举类名{名称:值,"说明";...}，不写类名时用 实体名+字段名+Enum
   *
   * @return enumStr、enumName、items（key、code、desc），注释中没有枚举时返回 null
   */
  static Kv enumType(String entityName, String javaField, String comment) {
    if (comment == null) {
      return null;
    }
    Matcher matcher = ENUM.matcher(comment);
    if (!matcher.find()) {
      return null;
    }
    String enumName = matcher.group(1);
    if (enumName == null || enumName.isEmpty()) {
      enumName = entityName + capitalize(javaField) + "Enum";
    }
    String body = matcher.group(2).trim();
    Matcher items = ENUM_ITEM.matcher(body.endsWith(";") ? body : body + ";");
    List<Kv> list = new java.util.ArrayList<>();
    while (items.find()) {
      String key = items.group(1).trim();
      String code = key;
      String desc = key;
      for (String part : items.group(2).split(",")) {
        String value = part.trim();
        if (value.startsWith("\"")) {
          desc = value.replace("\"", "");
        } else if (!value.isEmpty()) {
          code = value;
        }
      }
      list.add(Kv.by("key", key.replace('-', '_').toUpperCase(Locale.ROOT)).set("code", code).set("desc", desc));
    }
    return Kv.by("enumStr", matcher.group()).set("enumName", enumName).set("items", list);
  }

  /** 解析注释中的字典：说明 [值-名称 值-名称]，用于生成字典的初始化 SQL */
  static Kv dict(String dictType, String fieldComment, String comment) {
    if (dictType == null || dictType.isEmpty() || comment == null) {
      return null;
    }
    Matcher matcher = DICT_ITEMS.matcher(comment);
    if (!matcher.find()) {
      return null;
    }
    List<Kv> items = new java.util.ArrayList<>();
    for (String item : matcher.group(1).trim().split(" +")) {
      int dash = item.indexOf('-');
      if (dash > 0) {
        items.add(Kv.by("key", item.substring(0, dash)).set("value", item.substring(dash + 1)));
      }
    }
    String value = fieldComment == null || fieldComment.isEmpty() ? dictType : fieldComment;
    return Kv.by("str", matcher.group()).set("key", dictType).set("value", value).set("itemList", items);
  }
}
