package nexus.io.tio.chat.service.base.crud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;

/**
 * 回显：把字典值、组织、岗位等 id 翻译成名称，写入 echoMap
 */
public class EchoService {

  public void fill(List<Kv> rows, Map<String, String> rules) {
    for (Kv row : rows) {
      Kv echoMap = Kv.create();
      for (Map.Entry<String, String> rule : rules.entrySet()) {
        Object value = row.get(rule.getKey());
        if (value == null) {
          continue;
        }
        Object name = translate(rule.getValue(), value);
        if (name != null) {
          echoMap.set(rule.getKey(), name);
        }
      }
      row.set("echoMap", echoMap);
    }
  }

  private Object translate(String rule, Object value) {
    if (value instanceof Collection) {
      List<Object> names = new ArrayList<>();
      for (Object item : (Collection<?>) value) {
        Object name = translate(rule, item);
        if (name != null) {
          names.add(name);
        }
      }
      return names;
    }
    String text = value.toString();
    if (rule.startsWith("dict:")) {
      return Db.queryStr("select name from def_dict where parent_key=? and key_=? and is_del=0 limit 1", rule.substring(5), text);
    }
    if ("org".equals(rule)) {
      return Db.queryStr("select name from base_org where id=?", text);
    }
    if ("position".equals(rule)) {
      return Db.queryStr("select name from base_position where id=?", text);
    }
    if ("user".equals(rule)) {
      return Db.queryStr("select nick_name from def_user where id=?", text);
    }
    return null;
  }
}
