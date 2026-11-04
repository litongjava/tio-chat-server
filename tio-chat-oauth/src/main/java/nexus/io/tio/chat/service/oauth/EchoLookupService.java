package nexus.io.tio.chat.service.oauth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.utils.Values;

/**
 * 回显查询：按 id 批量返回名称，字典按类型返回“类型-字典项”到名称的映射
 */
public class EchoLookupService {
  private static final String DICT_SEPARATOR = "###";

  public Map<String, Object> userNames(List<String> ids) {
    return names("def_user", "nick_name", ids);
  }

  public Map<String, Object> positionNames(List<String> ids) {
    return names("base_position", "name", ids);
  }

  public Map<String, Object> orgNames(List<String> ids) {
    return names("base_org", "name", ids);
  }

  public Map<String, Object> dictNames(List<String> dictKeys) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String dictKey : dictKeys) {
      List<Kv> items = Db.findMaps("select key_, name from def_dict where parent_key=? and is_del=0", dictKey);
      for (Kv item : items) {
        result.put(dictKey + DICT_SEPARATOR + item.getStr("key_"), item.getStr("name"));
      }
    }
    return result;
  }

  private Map<String, Object> names(String table, String column, List<String> ids) {
    Map<String, Object> result = new LinkedHashMap<>();
    List<Long> longs = Values.toLongList(new ArrayList<Object>(ids));
    if (longs.isEmpty()) {
      return result;
    }
    String marks = String.join(",", Collections.nCopies(longs.size(), "?"));
    String sql = "select id, " + column + " as echo_name from " + table + " where is_del=0 and id in (" + marks + ")";
    for (Kv row : Db.findMaps(sql, longs.toArray())) {
      result.put(row.getStr("id"), row.getStr("echo_name"));
    }
    return result;
  }
}
