package ${enumPackage};

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;

/**
 * ${(table.swaggerComment!table.name)?replace("*/", "* /")}的回显：字典类型常量，并把枚举和字典的名称写入 echoMap
 *
 * @author ${author}
 * @date ${datetime}
 */
public final class ${table.entityName}Echo {
<#list echoFields as field>
<#if field.dictType??>
  /** ${field.comment?replace("*/", "* /")} */
  public static final String ${field.constant} = "${field.dictType?j_string}";
</#if>
</#list>

  private ${table.entityName}Echo() {
  }

  public static void fill(List<Kv> records) {
    if (records.isEmpty()) {
      return;
    }
<#list echoFields as field>
<#if field.dictType??>
    Map<String, String> ${field.javaField}Names = dict(${field.constant});
<#else>
    Map<String, String> ${field.javaField}Names = new HashMap<>();
    for (${field.enumName} item : ${field.enumName}.values()) {
      ${field.javaField}Names.put(item.getCode(), item.getDesc());
    }
</#if>
</#list>
    for (Kv record : records) {
      Kv echo = Kv.create();
<#list echoFields as field>
      if (record.get("${field.javaField}") != null) {
        echo.set("${field.javaField}", ${field.javaField}Names.get(String.valueOf(record.get("${field.javaField}"))));
      }
</#list>
      record.set("echoMap", echo);
    }
  }

  private static Map<String, String> dict(String type) {
    Map<String, String> names = new HashMap<>();
    String sql = """
        select key_, name from def_dict where parent_key=? and is_del=0
        """;
    for (Kv row : Db.findMaps(sql, type)) {
      names.put(row.getStr("key_"), row.getStr("name"));
    }
    return names;
  }
}
