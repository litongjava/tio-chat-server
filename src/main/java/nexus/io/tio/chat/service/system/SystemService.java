package nexus.io.tio.chat.service.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.common.SysConfigService;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * 系统初始化配置、参数维护与模型列表
 */
public class SystemService {

  public Kv init() {
    SysConfigService config = Aop.get(SysConfigService.class);
    Kv qiNiu = Kv.by("ossDomain", config.get("qnStorageCDN")).set("fragmentSize", config.get("fragmentSize"))
        .set("turnSharSize", config.get("turnSharSize"));
    String urlsRaw = config.get("iceServerUrls");
    List<String> urls = new ArrayList<>();
    if (urlsRaw != null && !urlsRaw.isBlank()) {
      try {
        for (Object url : JsonUtils.parseArray(urlsRaw, Object.class)) {
          urls.add(String.valueOf(url));
        }
      } catch (Exception e) {
        for (String url : urlsRaw.split(",")) {
          if (!url.trim().isEmpty()) {
            urls.add(url.trim());
          }
        }
      }
    }
    Kv ice = Kv.by("urls", urls).set("username", config.get("iceServerUsername")).set("credential",
        config.get("iceServerCredential"));
    return Kv.by("name", config.get("systemName")).set("logo", config.get("logo"))
        .set("roomGroupId", config.get("roomGroupId")).set("qiNiu", qiNiu).set("iceServer", ice);
  }

  public Kv storageProvider() {
    return Kv.by("provider", Aop.get(SysConfigService.class).get("storageDefault"));
  }

  public List<Kv> configList(String type, String configName, String configKey) {
    StringBuilder sql = new StringBuilder("select * from base_config where is_del=0");
    List<Object> params = new ArrayList<>();
    if (type != null) {
      sql.append(" and type=?");
      params.add(type);
    }
    if (configName != null) {
      sql.append(" and config_name like ?");
      params.add("%" + configName + "%");
    }
    if (configKey != null) {
      sql.append(" and config_key like ?");
      params.add("%" + configKey + "%");
    }
    sql.append(" order by id desc");
    List<Kv> result = new ArrayList<>();
    for (Kv row : Db.findMaps(sql.toString(), params.toArray())) {
      result.add(configVo(row));
    }
    return result;
  }

  private Kv configVo(Kv row) {
    return Kv.by("id", Values.toLong(row.get("id"))).set("type", row.getStr("type")).set("configName", row.getStr("config_name"))
        .set("configKey", row.getStr("config_key")).set("configValue", row.getStr("config_value"))
        .set("isDel", Values.toInt(row.get("is_del"))).set("createTime", Values.time(row.get("create_time")))
        .set("updateTime", Values.time(row.get("update_time"))).set("createBy", row.getLong("create_by"))
        .set("updateBy", row.getLong("update_by")).set("tenantId", row.getLong("tenant_id"));
  }

  public boolean updateConfig(Map<String, Object> config) {
    Long id = Values.toLong(config.get("id"));
    if (id == null) {
      return false;
    }
    String sql = """
        update base_config set type=coalesce(?, type), config_name=coalesce(?, config_name),
          config_key=coalesce(?, config_key), config_value=coalesce(?, config_value)
        where id=?
        """;
    return Db.update(sql, config.get("type"), config.get("configName"), config.get("configKey"), config.get("configValue"),
        id) > 0;
  }

  public List<Kv> models() {
    List<Kv> result = new ArrayList<>();
    for (Kv row : Db.findMaps("select * from sys_model where is_del=0 order by id")) {
      result.add(Kv.by("id", row.getLong("id")).set("modelKey", row.getStr("model_key")).set("modelName", row.getStr("model_name"))
          .set("modelUrl", row.getStr("model_url")).set("description", row.getStr("description"))
          .set("version", row.getStr("version")).set("status", Values.toBool(row.get("status"))));
    }
    return result;
  }
}
