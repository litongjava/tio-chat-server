package nexus.io.tio.chat.service.common;

import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;

/**
 * 读取 base_config 系统配置
 */
public class SysConfigService {

  public String get(String key) {
    return Db.queryStr("select config_value from base_config where config_key=? and is_del=0 limit 1", key);
  }

  public String get(String key, String defaultValue) {
    String value = get(key);
    return value == null ? defaultValue : value;
  }

  public List<Kv> listByType(String type) {
    String sql = """
        select id, type, config_name, config_key, config_value
        from base_config
        where is_del=0 and (? is null or type=?)
        order by id
        """;
    return Db.findMaps(sql, type, type);
  }
}
