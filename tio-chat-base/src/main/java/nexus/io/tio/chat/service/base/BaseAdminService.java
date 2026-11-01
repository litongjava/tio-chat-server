package nexus.io.tio.chat.service.base;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jfinal.kit.Kv;

import cn.hutool.core.util.RandomUtil;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.chat.service.base.crud.CrudService;
import nexus.io.tio.chat.service.base.crud.EchoService;
import nexus.io.tio.chat.utils.AccountUtils;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.environment.EnvUtils;

/**
 * 后台管理的扩展接口：唯一性校验、树形结构、授权关系、用户状态与日志清理
 */
public class BaseAdminService {
  public static final List<String> ADMIN_ROLE_CODES = List.of("SUPER_ADMIN", "TENANT_ADMIN");

  /**
   * 判断某列取值是否已被其他记录占用，extraColumn 为同时限定的列
   */
  public boolean exists(String table, String column, Object value, Long excludeId, String extraColumn, Object extraValue) {
    StringBuilder sql = new StringBuilder("select count(*) from ").append(table).append(" where ").append(column).append("=?");
    List<Object> params = new ArrayList<>();
    params.add(value);
    if (extraColumn != null) {
      sql.append(" and ").append(extraColumn).append("=?");
      params.add(extraValue);
    }
    if (excludeId != null) {
      sql.append(" and id<>?");
      params.add(excludeId);
    }
    if (columns(table).contains("is_del")) {
      sql.append(" and is_del=0");
    }
    return Db.queryLong(sql.toString(), params.toArray()) > 0;
  }

  private List<String> columns(String table) {
    return Db.query("select column_name from information_schema.columns where table_schema=database() and table_name=?", table);
  }

  // ---------------------------------------------------------------- 树

  /**
   * 按 parentId 组装树，节点子集放在 children
   */
  public List<Kv> buildTree(List<Kv> nodes) {
    Map<Object, Kv> byId = new LinkedHashMap<>();
    for (Kv node : nodes) {
      byId.put(String.valueOf(node.get("id")), node);
    }
    List<Kv> roots = new ArrayList<>();
    for (Kv node : nodes) {
      Object parentId = node.get("parentId");
      Kv parent = parentId == null ? null : byId.get(String.valueOf(parentId));
      if (parent == null) {
        roots.add(node);
      } else {
        @SuppressWarnings("unchecked")
        List<Kv> children = (List<Kv>) parent.computeIfAbsent("children", k -> new ArrayList<Kv>());
        children.add(node);
      }
    }
    return roots;
  }

  public List<Kv> tree(CrudDef def, Map<String, Object> model) {
    CrudService crudService = Aop.get(CrudService.class);
    List<Kv> list = crudService.query(def, model == null ? Map.of() : model);
    list.sort((a, b) -> Integer.compare(sortValue(a), sortValue(b)));
    Aop.get(EchoService.class).fill(list, def.getEcho());
    return buildTree(list);
  }

  private int sortValue(Kv row) {
    Integer value = Values.toInt(row.get("sortValue"));
    return value == null ? 0 : value;
  }

  public void moveResource(Long id, Long parentId) {
    Long newParent = parentId == null ? 0L : parentId;
    String parentPath = newParent == 0 ? "/" : Db.queryStr("select tree_path from def_resource where id=?", newParent);
    Integer parentGrade = newParent == 0 ? -1 : Db.queryInt("select tree_grade from def_resource where id=?", newParent);
    String treePath = (parentPath == null ? "/" : parentPath) + (newParent == 0 ? "" : newParent + "/");
    Db.update("update def_resource set parent_id=?, tree_path=?, tree_grade=? where id=?", newParent, treePath,
        parentGrade == null ? 0 : parentGrade + 1, id);
  }

  public List<Kv> areaLazyList(Long parentId) {
    CrudService crudService = Aop.get(CrudService.class);
    List<Kv> result = new ArrayList<>();
    for (Kv row : Db.findMaps("select * from def_area where parent_id=? and is_del=0 order by sort_value", parentId)) {
      Kv vo = crudService.toVo(CrudDef.of("/defArea", "def_area"), row);
      vo.set("hasChildren", Db.queryLong("select count(*) from def_area where parent_id=? and is_del=0", row.getLong("id")) > 0);
      result.add(vo);
    }
    return result;
  }

  // ---------------------------------------------------------------- 角色与授权

  public List<Long> saveEmployeeRoles(Long employeeId, List<Long> roleIds, Boolean flag) {
    if (roleIds != null && !roleIds.isEmpty()) {
      Db.update("delete from base_employee_role_rel where employee_id=? and role_id in (" + Values.placeholders(roleIds.size()) + ")",
          concat(employeeId, roleIds));
      if (flag == null || flag) {
        for (Long roleId : roleIds) {
          DbRows.save("base_employee_role_rel", Row.by("employee_id", employeeId).set("role_id", roleId).set("create_by", operator()));
        }
      }
    }
    return employeeRoleIds(employeeId);
  }

  public List<Long> employeeRoleIds(Long employeeId) {
    return Db.query("select role_id from base_employee_role_rel where employee_id=? and is_del=0", employeeId);
  }

  public List<Long> saveRoleEmployees(Long roleId, List<Long> employeeIds, Boolean flag) {
    if (employeeIds != null && !employeeIds.isEmpty()) {
      Db.update("delete from base_employee_role_rel where role_id=? and employee_id in (" + Values.placeholders(employeeIds.size()) + ")",
          concat(roleId, employeeIds));
      if (flag == null || flag) {
        for (Long employeeId : employeeIds) {
          DbRows.save("base_employee_role_rel", Row.by("employee_id", employeeId).set("role_id", roleId).set("create_by", operator()));
        }
      }
    }
    return roleEmployeeIds(roleId);
  }

  public List<Long> roleEmployeeIds(Long roleId) {
    return Db.query("select employee_id from base_employee_role_rel where role_id=? and is_del=0", roleId);
  }

  public List<Long> saveOrgRoles(Long orgId, List<Long> roleIds, Boolean flag) {
    if (roleIds != null && !roleIds.isEmpty()) {
      Db.update("delete from base_org_role_rel where org_id=? and role_id in (" + Values.placeholders(roleIds.size()) + ")",
          concat(orgId, roleIds));
      if (flag == null || flag) {
        for (Long roleId : roleIds) {
          DbRows.save("base_org_role_rel", Row.by("org_id", orgId).set("role_id", roleId).set("create_by", operator()));
        }
      }
    }
    return orgRoleIds(orgId);
  }

  public List<Long> orgRoleIds(Long orgId) {
    return Db.query("select role_id from base_org_role_rel where org_id=? and is_del=0", orgId);
  }

  /**
   * 保存角色的资源授权：applicationId -> resourceId 列表
   */
  public boolean saveRoleResources(Long roleId, Map<String, Object> applicationResourceMap) {
    Db.update("delete from base_role_resource_rel where role_id=?", roleId);
    if (applicationResourceMap == null || applicationResourceMap.isEmpty()) {
      return false;
    }
    for (Map.Entry<String, Object> entry : applicationResourceMap.entrySet()) {
      Long applicationId = Values.toLong(entry.getKey());
      if (entry.getValue() instanceof Collection) {
        for (Long resourceId : Values.toLongList((Collection<?>) entry.getValue())) {
          DbRows.save("base_role_resource_rel", Row.by("role_id", roleId).set("resource_id", resourceId)
              .set("application_id", applicationId).set("create_by", operator()));
        }
      }
    }
    return true;
  }

  /**
   * 角色授权的资源，按应用分组；category=30 为数据权限资源
   */
  public Map<Long, List<Long>> roleResources(Long roleId, boolean dataScope) {
    String sql = """
        select rr.application_id, rr.resource_id from base_role_resource_rel rr
        join def_resource r on r.id=rr.resource_id
        where rr.role_id=? and rr.is_del=0 and %s
        """.formatted(dataScope ? "r.resource_type='60'" : "r.resource_type<>'60'");
    Map<Long, List<Long>> result = new LinkedHashMap<>();
    for (Kv row : Db.findMaps(sql, roleId)) {
      result.computeIfAbsent(row.getLong("application_id"), k -> new ArrayList<>()).add(row.getLong("resource_id"));
    }
    return result;
  }

  /**
   * 员工的角色：直接授予的角色与所在组织的角色
   */
  public List<Kv> employeeRoles(Long employeeId) {
    String sql = """
        select distinct r.* from base_role r where r.is_del=0 and r.state=1 and (
          r.id in (select role_id from base_employee_role_rel where employee_id=? and is_del=0)
          or r.id in (select orr.role_id from base_org_role_rel orr join base_employee_org_rel eo on eo.org_id=orr.org_id
                      where eo.employee_id=? and orr.is_del=0 and eo.is_del=0))
        """;
    return Db.findMaps(sql, employeeId, employeeId);
  }

  public List<String> employeeRoleCodes(Long employeeId) {
    List<String> codes = new ArrayList<>();
    for (Kv role : employeeRoles(employeeId)) {
      codes.add(role.getStr("code"));
    }
    return codes;
  }

  /**
   * 是否为管理员：拥有 SUPER_ADMIN 或 TENANT_ADMIN 角色（原系统中管理员拥有全部权限 "*"）
   */
  public boolean isAdmin(Long employeeId) {
    for (Kv role : employeeRoles(employeeId)) {
      if (ADMIN_ROLE_CODES.contains(role.getStr("code"))) {
        return true;
      }
    }
    return false;
  }

  /**
   * 员工可见的资源编码，管理员角色拥有全部资源
   */
  public List<String> employeeResourceCodes(Long employeeId, Long applicationId) {
    List<Long> roleIds = new ArrayList<>();
    boolean superAdmin = false;
    for (Kv role : employeeRoles(employeeId)) {
      roleIds.add(role.getLong("id"));
      if (ADMIN_ROLE_CODES.contains(role.getStr("code"))) {
        superAdmin = true;
      }
    }
    StringBuilder sql = new StringBuilder("select distinct r.code from def_resource r where r.is_del=0 and r.code is not null");
    List<Object> params = new ArrayList<>();
    if (applicationId != null) {
      sql.append(" and r.application_id=?");
      params.add(applicationId);
    }
    if (!superAdmin) {
      if (roleIds.isEmpty()) {
        return new ArrayList<>();
      }
      sql.append(" and r.id in (select resource_id from base_role_resource_rel where is_del=0 and role_id in (")
          .append(Values.placeholders(roleIds.size())).append("))");
      params.addAll(roleIds);
    }
    return Db.query(sql.toString(), params.toArray());
  }

  /**
   * 应用及其资源树
   */
  public List<Kv> applicationResources(boolean onlyAvailable, boolean dataScope) {
    CrudService crudService = Aop.get(CrudService.class);
    CrudDef appDef = CrudDef.of("/defApplication", "def_application");
    CrudDef resourceDef = CrudDef.of("/defResource", "def_resource");
    List<Kv> result = new ArrayList<>();
    String appSql = "select * from def_application where is_del=0" + (onlyAvailable ? " and is_visible=1" : "") + " order by sort_value";
    for (Kv app : Db.findMaps(appSql)) {
      String resSql = "select * from def_resource where is_del=0 and application_id=?" + (dataScope ? " and resource_type='60'" : "")
          + " order by sort_value";
      List<Kv> resources = new ArrayList<>();
      for (Kv row : Db.findMaps(resSql, app.getLong("id"))) {
        resources.add(crudService.toVo(resourceDef, row));
      }
      result.add(Kv.by("defApplication", crudService.toVo(appDef, app)).set("resourceList", buildTree(resources)));
    }
    return result;
  }

  // ---------------------------------------------------------------- 用户

  public boolean resetPassword(Long id, Boolean useSystemPassword, String password, String confirmPassword) {
    String newPassword = password;
    if (Boolean.TRUE.equals(useSystemPassword)) {
      newPassword = EnvUtils.get("chat.default.password", "123456");
    } else {
      ChatErrors.require(password != null && !password.isEmpty(), "请输入密码");
      ChatErrors.require(Objects.equals(password, confirmPassword), "密码和确认密码不一致");
    }
    Kv user = Db.findFirstMap("select id, salt from def_user where id=?", id);
    ChatErrors.require(user != null, "您要重置密码的用户不存在");
    String salt = user.getStr("salt") == null || user.getStr("salt").isEmpty() ? RandomUtil.randomString(20) : user.getStr("salt");
    return Db.update("update def_user set password=?, salt=?, password_error_num=0 where id=?",
        AccountUtils.hashPassword(newPassword, salt), salt, id) > 0;
  }

  // ---------------------------------------------------------------- 日志

  /**
   * type 1/2/3/4 清理 1/3/6/12 个月前的日志，5/6/7/8 只保留最近 1千/1万/3万/10万 条
   */
  public boolean clearLog(String table, Integer type) {
    int t = type == null ? 1 : type;
    int[] months = { 1, 3, 6, 12 };
    int[] keep = { 1000, 10000, 30000, 100000 };
    if (t >= 1 && t <= 4) {
      return Db.update("delete from " + table + " where create_time<?", LocalDateTime.now().minusMonths(months[t - 1])) >= 0;
    }
    if (t >= 5 && t <= 8) {
      Long boundary = Db.queryLong("select id from " + table + " order by id desc limit ?, 1", keep[t - 5]);
      if (boundary != null) {
        Db.update("delete from " + table + " where id<=?", boundary);
      }
      return true;
    }
    return false;
  }

  private Object[] concat(Object first, List<Long> rest) {
    List<Object> params = new ArrayList<>();
    params.add(first);
    params.addAll(rest);
    return params.toArray();
  }

  private Long operator() {
    return LoginContext.get() == null ? 0L : LoginContext.get().getUserId();
  }
}
