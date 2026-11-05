package nexus.io.tio.chat.service.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.config.route.BaseCrudRegistry;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.chat.service.base.crud.CrudService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;

/**
 * 管理后台（HuLa-Admin）的菜单接口。字段 type、sort 对应 resourceType、sortValue，业务规则和后台通用接口相同
 */
public class AdminResourceService {

  private CrudDef def() {
    return BaseCrudRegistry.find("/defResource");
  }

  private CrudService crud() {
    return Aop.get(CrudService.class);
  }

  /** routeName 可以是资源编码，也可以是地址栏路径 */
  public boolean routeExists(String routeName) {
    ChatErrors.require(routeName != null && !routeName.isBlank(), "routeName不能为空");
    String sql = """
        select count(*) from def_resource where is_del=0 and (code=? or path=?)
        """;
    return Db.queryLong(sql, routeName, routeName) > 0;
  }

  @SuppressWarnings("unchecked")
  public List<Kv> visible(Long applicationId) {
    Kv resource = Aop.get(BaseExtService.class).visibleResource(LoginContext.uid(), applicationId);
    return (List<Kv>) resource.get("routerList");
  }

  public Kv add(Map<String, Object> body) {
    return crud().save(def(), fromApi(body));
  }

  public Kv edit(Map<String, Object> body) {
    return crud().update(def(), fromApi(body));
  }

  /** 参数是 id 或 ids */
  public boolean delete(Map<String, Object> body) {
    List<Long> ids = new ArrayList<>();
    if (body.get("ids") instanceof Collection<?> list) {
      ids.addAll(Values.toLongList(list));
    }
    Long id = Values.toLong(body.get("id"));
    if (id != null) {
      ids.add(id);
    }
    ChatErrors.require(!ids.isEmpty(), "请选择要删除的资源");
    return crud().delete(def(), ids);
  }

  private Map<String, Object> fromApi(Map<String, Object> body) {
    Map<String, Object> result = new HashMap<>(body);
    if (result.containsKey("type")) {
      result.put("resourceType", result.remove("type"));
    }
    if (result.containsKey("sort")) {
      result.put("sortValue", result.remove("sort"));
    }
    return result;
  }
}
