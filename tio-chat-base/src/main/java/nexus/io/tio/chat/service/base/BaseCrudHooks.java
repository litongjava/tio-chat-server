package nexus.io.tio.chat.service.base;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jfinal.kit.Kv;

import cn.hutool.core.util.RandomUtil;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.tio.chat.service.base.crud.CrudHook;
import nexus.io.tio.chat.utils.AccountUtils;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.UserAgents;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 基础数据的业务规则，与原后端各资源的 Service 保持一致
 */
public final class BaseCrudHooks {
  private static final String SYSTEM_VALUE = "10";
  private static final String BUSINESS_VALUE = "20";
  private static final String DICT_SYSTEM = "10";
  private static final long ROOT_ID = 0L;
  private static final String APPENDIX_APPLICATION_LOGO = "DEF__APPLICATION__LOGO";
  private static final String APPENDIX_TENANT_LOGO = "DEF__TENANT__LOGO";
  private static final String RESOURCE_MENU = "20";
  private static final String EXEC_IMPL_CLASS = "01";
  private static final int TENANT_WAITING = 1;
  private static final int TENANT_CREATE = 0;
  private static final String TENANT_NOT_EXPIRE = "2099-12-31 23:59:59";
  private static final String EMPLOYEE_ACTIVATED = "20";
  private static final int EMPLOYEE_NOT_ADMIN = 2;
  private static final Pattern TEMPLATE_PARAM = Pattern.compile("\\$\\{([^}]+)}");

  private BaseCrudHooks() {
  }

  /** 这一列的值在表里是否已被其他数据使用 */
  private static boolean used(String table, String column, Object value, Long id, String extra, Object... extraParams) {
    List<Object> params = new ArrayList<>();
    params.add(value);
    StringBuilder sql = new StringBuilder("select count(*) from ").append(table).append(" where is_del=0 and ").append(column).append("=?");
    if (id != null) {
      sql.append(" and id<>?");
      params.add(id);
    }
    if (extra != null) {
      sql.append(" and ").append(extra);
      params.addAll(List.of(extraParams));
    }
    return Db.queryLong(sql.toString(), params.toArray()) > 0;
  }

  /** 列出现在请求中时检查唯一性 */
  private static void unique(Row row, String table, String column, Long id, String message) {
    Object value = row.get(column);
    if (value != null && !value.toString().isBlank()) {
      ChatErrors.require(!used(table, column, value, id, null), message + value + " 已存在");
    }
  }

  private static void softDelete(String table, String column, Collection<Long> ids) {
    if (!ids.isEmpty()) {
      Db.update("update " + table + " set is_del=1 where " + column + " in (" + Values.placeholders(ids.size()) + ")", ids.toArray());
    }
  }

  private static long count(String table, String column, Collection<Long> ids) {
    if (ids.isEmpty()) {
      return 0;
    }
    return Db.queryLong("select count(*) from " + table + " where is_del=0 and " + column + " in (" + Values.placeholders(ids.size()) + ")", ids.toArray());
  }

  /** 附件表：业务数据和文件的关系，一个业务类型保留一个文件 */
  private static void saveAppendix(Long bizId, String bizType, Object fileId) {
    Long file = Values.toLong(fileId);
    if (file == null) {
      return;
    }
    Db.update("delete from base_com_appendix where biz_id=? and biz_type=?", bizId, bizType);
    Db.update("delete from base_com_appendix where id=?", file);
    DbRows.save("base_com_appendix", Row.by("id", file).set("biz_id", bizId).set("biz_type", bizType));
  }

  private static void removeAppendix(Collection<Long> bizIds, String bizType) {
    if (!bizIds.isEmpty()) {
      List<Object> params = new ArrayList<>(bizIds);
      params.add(bizType);
      Db.update("delete from base_com_appendix where biz_id in (" + Values.placeholders(bizIds.size()) + ") and biz_type=?", params.toArray());
    }
  }

  private static String appendixId(Long bizId, String bizType) {
    Long file = Db.queryLong("select id from base_com_appendix where biz_id=? and biz_type=? and is_del=0 limit 1", bizId, bizType);
    return file == null ? null : String.valueOf(file);
  }

  public static final CrudHook USER = new CrudHook() {
    private void check(Row row, Long id) {
      unique(row, "def_user", "username", id, "用户名：");
      unique(row, "def_user", "email", id, "邮箱：");
      unique(row, "def_user", "mobile", id, "手机号：");
      unique(row, "def_user", "id_card", id, "身份证号：");
    }

    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      check(row, null);
      String salt = RandomUtil.randomString(20);
      String password = row.getStr("password");
      if (password == null || password.isEmpty()) {
        password = EnvUtils.get("chat.user.default-password", "123456");
      }
      row.set("salt", salt).set("password", AccountUtils.hashPassword(password, salt)).set("password_error_num", 0);
    }

    @Override
    public void beforeUpdate(Long id, Map<String, Object> body, Row row) {
      check(row, id);
      // 修改密码有单独的接口，这里写入会保存成明文
      row.remove("password");
      row.remove("salt");
    }
  };

  public static final CrudHook EMPLOYEE = new CrudHook() {
    @SuppressWarnings("unchecked")
    private void saveOrgs(Long employeeId, Map<String, Object> body) {
      if (!body.containsKey("orgIdList")) {
        return;
      }
      Db.update("update base_employee_org_rel set is_del=1 where employee_id=?", employeeId);
      if (body.get("orgIdList") instanceof Collection<?> orgIds) {
        for (Long orgId : Values.toLongList((Collection<Object>) orgIds)) {
          DbRows.save("base_employee_org_rel", Row.by("org_id", orgId).set("employee_id", employeeId));
        }
      }
    }

    /** 没有指定用户时按姓名和手机号创建用户，再保存员工 */
    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      if (row.get("user_id") == null) {
        String mobile = (String) body.get("mobile");
        ChatErrors.require(mobile != null && !mobile.isBlank(), "请填写登录手机号");
        String realName = (String) body.get("realName");
        ChatErrors.require(realName != null && !realName.isBlank(), "请填写真实姓名");
        ChatErrors.require(!used("def_user", "mobile", mobile, null, null), "手机号已被注册,请重新输入手机号 或 直接邀请它加入贵公司");
        String username = (String) body.get("username");
        Row user = Row.by("id", SnowflakeIdUtils.id()).set("username", username == null || username.isBlank() ? RandomUtil.randomString(32) : username)
            .set("nick_name", realName).set("mobile", mobile).set("sex", body.get("sex")).set("state", true);
        USER.beforeSave(body, user);
        DbRows.save("def_user", user);
        row.set("user_id", user.getLong("id")).set("name", realName);
      }
      row.set("active_status", EMPLOYEE_ACTIVATED);
      row.getColumns().putIfAbsent("admin", EMPLOYEE_NOT_ADMIN);
    }

    @Override
    public void afterSave(Map<String, Object> body, Row row) {
      saveOrgs(row.getLong("id"), body);
    }

    @Override
    public void afterUpdate(Long id, Map<String, Object> body) {
      saveOrgs(id, body);
    }

    @Override
    public void beforeDelete(Collection<Long> ids) {
      softDelete("base_employee_org_rel", "employee_id", ids);
      softDelete("base_employee_role_rel", "employee_id", ids);
    }

    /** 按用户信息、组织和角色筛选员工。scope 为 1 查询拥有这个角色的员工，其他值查询没有的 */
    @Override
    public void appendWhere(Map<String, Object> model, StringBuilder where, List<Object> params) {
      List<String> userFilters = new ArrayList<>();
      List<Object> userParams = new ArrayList<>();
      for (String[] filter : new String[][] { { "mobile", "mobile" }, { "email", "email" }, { "username", "username" }, { "idCard", "id_card" } }) {
        Object value = model.get(filter[0]);
        if (value != null && !value.toString().isEmpty()) {
          userFilters.add(filter[1] + " like ?");
          userParams.add("%" + value + "%");
        }
      }
      if (!userFilters.isEmpty()) {
        where.append(" and user_id in (select id from def_user where is_del=0 and ").append(String.join(" and ", userFilters)).append(")");
        params.addAll(userParams);
      }
      if (model.get("orgIdList") instanceof Collection<?> orgIds && !orgIds.isEmpty()) {
        where.append(" and id in (select employee_id from base_employee_org_rel where is_del=0 and org_id in (").append(Values.placeholders(orgIds.size())).append("))");
        params.addAll(orgIds);
      }
      Long roleId = Values.toLong(model.get("roleId"));
      Object scope = model.get("scope");
      if (roleId != null && scope != null && !"-1".equals(scope.toString())) {
        where.append("1".equals(scope.toString()) ? " and " : " and not ");
        where.append("exists (select 1 from base_employee_role_rel r where r.employee_id=base_employee.id and r.role_id=? and r.is_del=0)");
        params.add(roleId);
      }
    }

    @Override
    public void decorateList(List<Kv> records) {
      for (Kv record : records) {
        decorateDetail(record);
      }
    }

    @Override
    public void decorateDetail(Kv vo) {
      List<String> orgIds = new ArrayList<>();
      for (Kv rel : Db.findMaps("select org_id from base_employee_org_rel where employee_id=? and is_del=0", Values.toLong(vo.get("id")))) {
        orgIds.add(String.valueOf(rel.getLong("org_id")));
      }
      vo.set("orgIdList", orgIds);
    }
  };

  public static final CrudHook ORG = new CrudHook() {
    private void check(Row row, Long id) {
      String name = row.getStr("name");
      if (name != null) {
        Long parentId = Values.toLong(row.get("parent_id"));
        boolean exists = used("base_org", "name", name, id, "parent_id=?", parentId == null ? ROOT_ID : parentId);
        ChatErrors.require(!exists, "组织[" + name + "]已经存在");
      }
    }

    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      ChatErrors.require(row.getStr("name") != null && !row.getStr("name").isBlank(), "请填写名称");
      check(row, null);
      row.getColumns().putIfAbsent("parent_id", ROOT_ID);
      BaseTrees.fill(row, "base_org", "组织");
    }

    @Override
    public void beforeUpdate(Long id, Map<String, Object> body, Row row) {
      check(row, id);
      BaseTrees.beforeMove(id, row, "base_org", "组织");
    }

    @Override
    public void beforeDelete(Collection<Long> ids) {
      ChatErrors.require(count("base_employee_org_rel", "org_id", ids) == 0, "您选择的组织下还存在用户，禁止删除！请先移除该组织下所有用户后再进行删除");
      ChatErrors.require(count("base_org", "parent_id", ids) == 0, "您选择的组织下还存在子组织，禁止删除！请先移除该组织下所有子组织后再进行删除");
      softDelete("base_org_role_rel", "org_id", ids);
    }
  };

  public static final CrudHook POSITION = new CrudHook() {
    private void check(Row row, Long id) {
      String name = row.getStr("name");
      if (name != null && row.get("org_id") != null) {
        ChatErrors.require(!used("base_position", "name", name, id, "org_id=?", row.get("org_id")), "岗位[" + name + "]已经存在");
      }
    }

    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      check(row, null);
    }

    @Override
    public void beforeUpdate(Long id, Map<String, Object> body, Row row) {
      check(row, id);
    }
  };

  public static final CrudHook ROLE = new CrudHook() {
    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      unique(row, "base_role", "code", null, "角色编码");
      if (row.getStr("code") == null || row.getStr("code").isBlank()) {
        row.set("code", RandomUtil.randomString(8));
      }
      row.set("type_", BUSINESS_VALUE).set("readonly_", false);
    }

    @Override
    public void beforeUpdate(Long id, Map<String, Object> body, Row row) {
      unique(row, "base_role", "code", id, "角色编码");
      row.remove("readonly_");
    }

    @Override
    public void beforeDelete(Collection<Long> ids) {
      if (ids.isEmpty()) {
        return;
      }
      long readonly = Db.queryLong("select count(*) from base_role where readonly_=1 and id in (" + Values.placeholders(ids.size()) + ")", ids.toArray());
      ChatErrors.require(readonly == 0, "内置角色不允许删除");
      softDelete("base_employee_role_rel", "role_id", ids);
      softDelete("base_org_role_rel", "role_id", ids);
      softDelete("base_role_resource_rel", "role_id", ids);
    }
  };

  public static final CrudHook TENANT = new CrudHook() {
    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      row.set("status", TENANT_WAITING).set("register_type", TENANT_CREATE);
      row.getColumns().putIfAbsent("package_id", 0L);
      row.getColumns().putIfAbsent("account_count", 0);
      row.getColumns().putIfAbsent("expire_time", TENANT_NOT_EXPIRE);
    }

    @Override
    public void afterSave(Map<String, Object> body, Row row) {
      TENANT_LOGO.afterSave(body, row);
    }

    @Override
    public void afterUpdate(Long id, Map<String, Object> body) {
      TENANT_LOGO.afterUpdate(id, body);
    }

    @Override
    public void beforeDelete(Collection<Long> ids) {
      TENANT_LOGO.beforeDelete(ids);
    }

    @Override
    public void decorateDetail(Kv vo) {
      TENANT_LOGO.decorateDetail(vo);
    }
  };

  /** 企业 logo，普通用户申请租户时也可以上传 */
  public static final CrudHook TENANT_LOGO = new CrudHook() {
    @Override
    public void afterSave(Map<String, Object> body, Row row) {
      saveAppendix(row.getLong("id"), APPENDIX_TENANT_LOGO, body.get("logo"));
    }

    @Override
    public void afterUpdate(Long id, Map<String, Object> body) {
      saveAppendix(id, APPENDIX_TENANT_LOGO, body.get("logo"));
    }

    @Override
    public void beforeDelete(Collection<Long> ids) {
      removeAppendix(ids, APPENDIX_TENANT_LOGO);
    }

    @Override
    public void decorateDetail(Kv vo) {
      vo.set("logo", appendixId(Values.toLong(vo.get("id")), APPENDIX_TENANT_LOGO));
    }
  };

  public static final CrudHook LOGIN_LOG = new CrudHook() {
    /** 按 userId、手机号或用户名找到用户，写入日志 */
    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      row.set("login_date", LocalDate.now().toString());
      UserAgents.fill(row, row.getStr("ua"));
      Kv user = null;
      Long userId = Values.toLong(row.get("user_id"));
      if (userId != null) {
        user = Db.findFirstMap("select id, username, nick_name from def_user where id=? and is_del=0", userId);
      } else if (body.get("mobile") != null) {
        user = Db.findFirstMap("select id, username, nick_name from def_user where mobile=? and is_del=0", body.get("mobile"));
      } else if (row.get("username") != null) {
        user = Db.findFirstMap("select id, username, nick_name from def_user where username=? and is_del=0", (Object) row.get("username"));
      }
      if (user != null) {
        row.set("username", user.get("username")).set("user_id", user.get("id")).set("nick_name", user.get("nick_name"));
      }
    }
  };

  public static final CrudHook APPLICATION = new CrudHook() {
    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      unique(row, "def_application", "name", null, "应用名称");
      row.set("app_key", RandomUtil.randomString(12)).set("app_secret", RandomUtil.randomString(36)).set("is_visible", true);
    }

    @Override
    public void beforeUpdate(Long id, Map<String, Object> body, Row row) {
      unique(row, "def_application", "name", id, "应用名称");
    }

    @Override
    public void afterSave(Map<String, Object> body, Row row) {
      saveAppendix(row.getLong("id"), APPENDIX_APPLICATION_LOGO, body.get("appendixIcon"));
    }

    @Override
    public void afterUpdate(Long id, Map<String, Object> body) {
      saveAppendix(id, APPENDIX_APPLICATION_LOGO, body.get("appendixIcon"));
    }

    @Override
    public void beforeDelete(Collection<Long> ids) {
      removeAppendix(ids, APPENDIX_APPLICATION_LOGO);
    }

    @Override
    public void decorateDetail(Kv vo) {
      vo.set("appendixIcon", appendixId(Values.toLong(vo.get("id")), APPENDIX_APPLICATION_LOGO));
    }
  };

  public static final CrudHook CLIENT = new CrudHook() {
    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      row.set("client_id", RandomUtil.randomString(24)).set("client_secret", RandomUtil.randomString(32));
    }
  };

  public static final CrudHook PARAMETER = new CrudHook() {
    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      unique(row, "def_parameter", "key_", null, "参数键");
      row.set("param_type", SYSTEM_VALUE);
    }

    @Override
    public void beforeUpdate(Long id, Map<String, Object> body, Row row) {
      unique(row, "def_parameter", "key_", id, "参数键");
    }
  };

  public static final CrudHook AREA = new CrudHook() {
    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      ChatErrors.require(row.getStr("code") != null && !row.getStr("code").isBlank(), "请填写地区编码");
      unique(row, "def_area", "code", null, "地区编码");
      row.getColumns().putIfAbsent("parent_id", ROOT_ID);
      BaseTrees.fill(row, "def_area", "地区");
    }

    @Override
    public void beforeUpdate(Long id, Map<String, Object> body, Row row) {
      unique(row, "def_area", "code", id, "地区编码");
      BaseTrees.beforeMove(id, row, "def_area", "地区");
    }
  };

  public static final CrudHook INTERFACE = new CrudHook() {
    private void check(Row row, Long id) {
      unique(row, "def_interface", "code", id, "接口编码");
      if (row.get("exec_mode") == null) {
        return;
      }
      boolean implClass = EXEC_IMPL_CLASS.equals(String.valueOf((Object) row.get("exec_mode")));
      String value = row.getStr(implClass ? "impl_class" : "script");
      ChatErrors.require(value != null && !value.isBlank(), implClass ? "请填写实现类" : "请填写实现脚本");
    }

    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      check(row, null);
    }

    @Override
    public void beforeUpdate(Long id, Map<String, Object> body, Row row) {
      check(row, id);
    }

    @Override
    public void beforeDelete(Collection<Long> ids) {
      softDelete("def_interface_property", "interface_id", ids);
    }
  };

  public static final CrudHook INTERFACE_LOG = new CrudHook() {
    @Override
    public void beforeDelete(Collection<Long> ids) {
      softDelete("extend_interface_logging", "log_id", ids);
    }
  };

  public static final CrudHook MSG_TEMPLATE = new CrudHook() {
    private void fillParam(Row row, Long id) {
      unique(row, "def_msg_template", "code", id, "模板标识");
      if (row.getColumns().containsKey("title") || row.getColumns().containsKey("content")) {
        Set<Kv> params = new LinkedHashSet<>();
        for (String text : new String[] { row.getStr("title"), row.getStr("content") }) {
          if (text != null) {
            Matcher matcher = TEMPLATE_PARAM.matcher(text);
            while (matcher.find()) {
              params.add(Kv.by("key", matcher.group(1)).set("value", ""));
            }
          }
        }
        row.set("param", JsonUtils.toJson(params));
      }
    }

    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      fillParam(row, null);
    }

    @Override
    public void beforeUpdate(Long id, Map<String, Object> body, Row row) {
      fillParam(row, id);
    }
  };

  public static final CrudHook RESOURCE = new CrudHook() {
    private void check(Row row, Long id) {
      unique(row, "def_resource", "code", id, "【资源编码】：");
      if (!RESOURCE_MENU.equals(String.valueOf((Object) row.get("resource_type")))) {
        return;
      }
      ChatErrors.require(row.getStr("path") != null && !row.getStr("path").isBlank(), "请填写【地址栏路径】");
      ChatErrors.require(row.getStr("component") != null && !row.getStr("component").isBlank(), "请填写【页面路径】");
      Object application = row.get("application_id");
      ChatErrors.require(!used("def_resource", "name", row.getStr("name"), id, "resource_type=? and application_id=?", RESOURCE_MENU, application),
          "【名称】:" + row.getStr("name") + "重复");
      String path = row.getStr("path");
      if (!path.startsWith("http")) {
        ChatErrors.require(!used("def_resource", "path", path, id, "application_id=?", application), "【地址栏路径】:" + path + "重复");
      }
      Long parentId = Values.toLong(row.get("parent_id"));
      if (parentId != null && parentId > 0) {
        Kv parent = Db.findFirstMap("select resource_type, is_hidden from def_resource where id=? and is_del=0", parentId);
        if (parent != null) {
          ChatErrors.require(RESOURCE_MENU.equals(String.valueOf(parent.get("resource_type"))), "菜单只能挂载在菜单下级");
          ChatErrors.require(!Values.isTrue(parent.get("is_hidden")), "菜单不能挂载在隐藏菜单下级");
        }
      }
    }

    /** 公共资源和启用的资源，父节点也必须是公共资源和启用的；修改时子节点要先改回来才能取消 */
    private void checkGeneralAndState(Row row, Long id) {
      boolean general = Values.isTrue(row.get("is_general"));
      boolean state = row.get("state") == null || Values.isTrue(row.get("state"));
      Long parentId = Values.toLong(row.get("parent_id"));
      if (parentId != null && parentId > 0) {
        Kv parent = Db.findFirstMap("select name, is_general, state from def_resource where id=? and is_del=0", parentId);
        ChatErrors.require(parent != null, "父节点不存在");
        ChatErrors.require(!general || Values.isTrue(parent.get("is_general")), "请先将父节点【" + parent.getStr("name") + "】-“公共资源”字段修改为：“是”");
        ChatErrors.require(!state || Values.isTrue(parent.get("state")), "请先将父节点【" + parent.getStr("name") + "】-“状态”字段修改为：“启用”");
      }
      if (id == null) {
        return;
      }
      for (Kv child : Db.findMaps("select is_general, state from def_resource where parent_id=? and is_del=0", id)) {
        ChatErrors.require(general || !Values.isTrue(child.get("is_general")), "请将所有子节点的“公共资源”修改为“否”后再修改");
        ChatErrors.require(state || !Values.isTrue(child.get("state")), "请将所有子节点的“状态”修改为“禁用”后再修改");
      }
    }

    private void normalizeMeta(Row row) {
      if (!row.getColumns().containsKey("meta_json")) {
        return;
      }
      String meta = row.getStr("meta_json");
      if (meta == null || meta.isEmpty()) {
        row.set("meta_json", "");
        return;
      }
      try {
        row.set("meta_json", JsonUtils.toJson(JsonUtils.parseToMap(meta, String.class, Object.class)));
      } catch (RuntimeException e) {
        throw ChatErrors.of("【元数据】须满足JSON格式");
      }
    }

    /** 修改接口可以只传变化的字段，校验时补上原来的值 */
    private Row merged(Long id, Row row) {
      Kv current = Db.findFirstMap("select * from def_resource where id=? and is_del=0", id);
      ChatErrors.require(current != null, "资源不存在");
      Row merged = new Row();
      for (Object key : current.keySet()) {
        merged.set(key.toString(), current.get(key));
      }
      merged.getColumns().putAll(row.getColumns());
      return merged;
    }

    @SuppressWarnings("unchecked")
    private void saveApis(Long resourceId, Map<String, Object> body) {
      if (!(body.get("resourceApiList") instanceof Collection<?> apis)) {
        return;
      }
      Db.update("update def_resource_api set is_del=1 where resource_id=?", resourceId);
      for (Object item : apis) {
        Map<String, Object> api = (Map<String, Object>) item;
        DbRows.save("def_resource_api", Row.by("id", SnowflakeIdUtils.id()).set("resource_id", resourceId).set("controller", api.get("controller"))
            .set("spring_application_name", api.get("springApplicationName")).set("request_method", api.get("requestMethod")).set("name", api.get("name"))
            .set("uri", api.get("uri")).set("is_input", Values.isTrue(api.get("isInput"))));
      }
    }

    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      check(row, null);
      checkGeneralAndState(row, null);
      normalizeMeta(row);
      row.getColumns().putIfAbsent("parent_id", ROOT_ID);
      BaseTrees.fill(row, "def_resource", "资源");
    }

    @Override
    public void afterSave(Map<String, Object> body, Row row) {
      saveApis(row.getLong("id"), body);
    }

    @Override
    public void beforeUpdate(Long id, Map<String, Object> body, Row row) {
      Row merged = merged(id, row);
      check(merged, id);
      checkGeneralAndState(merged, id);
      normalizeMeta(row);
      BaseTrees.beforeMove(id, row, "def_resource", "资源");
    }

    @Override
    public void afterUpdate(Long id, Map<String, Object> body) {
      saveApis(id, body);
    }

    @Override
    public void beforeDelete(Collection<Long> ids) {
      ChatErrors.require(count("def_resource", "parent_id", ids) == 0, "您要删除的资源存在子资源，请先删除子资源！");
      softDelete("def_resource_api", "resource_id", ids);
      softDelete("base_role_resource_rel", "resource_id", ids);
    }

    @Override
    public void decorateDetail(Kv vo) {
      List<Kv> apis = new ArrayList<>();
      String sql = """
          select id, resource_id, controller, spring_application_name, request_method, name, uri, is_input from def_resource_api
          where resource_id=? and is_del=0
          """;
      for (Kv api : Db.findMaps(sql, Values.toLong(vo.get("id")))) {
        apis.add(Kv.by("id", String.valueOf(api.getLong("id"))).set("resourceId", String.valueOf(api.getLong("resource_id"))).set("controller", api.get("controller"))
            .set("springApplicationName", api.get("spring_application_name")).set("requestMethod", api.get("request_method")).set("name", api.get("name"))
            .set("uri", api.get("uri")).set("isInput", Values.isTrue(api.get("is_input"))));
      }
      vo.set("resourceApiList", apis);
    }
  };

  public static final CrudHook OPERATION_LOG = new CrudHook() {
    @Override
    public void afterSave(Map<String, Object> body, Row row) {
      DbRows.save("base_operation_log_ext", Row.by("id", row.getLong("id")).set("params", body.get("params")).set("result", body.get("result"))
          .set("ex_detail", body.get("exDetail")));
    }

    @Override
    public void beforeDelete(Collection<Long> ids) {
      softDelete("base_operation_log_ext", "id", ids);
    }

    @Override
    public void decorateDetail(Kv vo) {
      Kv ext = Db.findFirstMap("select params, result, ex_detail from base_operation_log_ext where id=? and is_del=0", Values.toLong(vo.get("id")));
      if (ext != null) {
        vo.set("params", ext.get("params")).set("result", ext.get("result")).set("exDetail", ext.get("ex_detail"));
      }
    }
  };

  /** 字典：parent_id 为 0 的是字典，其下是字典项 */
  public static final CrudHook DICT = new CrudHook() {
    private void checkKey(Row row, Long id) {
      Object key = row.get("key_");
      if (key != null) {
        ChatErrors.require(!used("def_dict", "key_", key, id, "parent_id=?", ROOT_ID), "字典标识" + key + "已存在");
      }
    }

    @SuppressWarnings("unchecked")
    private void saveItems(Object items, Long dictId, String dictKey) {
      if (!(items instanceof Collection<?> list)) {
        return;
      }
      for (Object item : list) {
        Map<String, Object> value = (Map<String, Object>) item;
        DbRows.save("def_dict", Row.by("id", SnowflakeIdUtils.id()).set("parent_id", dictId).set("parent_key", dictKey).set("classify", DICT_SYSTEM)
            .set("key_", value.get("key")).set("name", value.get("name")).set("state", value.get("state")).set("remark", value.get("remark"))
            .set("sort_value", value.get("sortValue")).set("icon", value.get("icon")).set("css_style", value.get("cssStyle")).set("css_class", value.get("cssClass")));
      }
    }

    @SuppressWarnings("unchecked")
    private void updateItems(Object items, Long dictId, String dictKey) {
      if (!(items instanceof Collection<?> list)) {
        return;
      }
      for (Object item : list) {
        Map<String, Object> value = (Map<String, Object>) item;
        Db.update("""
            update def_dict set key_=coalesce(?, key_), name=coalesce(?, name), state=coalesce(?, state), remark=coalesce(?, remark), parent_key=?
            where id=? and parent_id=?
            """, value.get("key"), value.get("name"), value.get("state"), value.get("remark"), dictKey, Values.toLong(value.get("id")), dictId);
      }
    }

    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      ChatErrors.require(row.getStr("key_") != null && !row.getStr("key_").isBlank(), "请填写字典标识");
      checkKey(row, null);
      row.set("classify", DICT_SYSTEM).set("parent_id", ROOT_ID).set("parent_key", "");
    }

    @Override
    public void afterSave(Map<String, Object> body, Row row) {
      saveItems(body.get("insertList"), row.getLong("id"), row.getStr("key_"));
    }

    @Override
    public void beforeUpdate(Long id, Map<String, Object> body, Row row) {
      checkKey(row, id);
      row.set("classify", DICT_SYSTEM);
      row.remove("parent_id");
    }

    @Override
    public void afterUpdate(Long id, Map<String, Object> body) {
      Kv dict = Db.findFirstMap("select key_ from def_dict where id=?", id);
      String key = dict == null ? null : dict.getStr("key_");
      saveItems(body.get("insertList"), id, key);
      updateItems(body.get("updateList"), id, key);
      if (body.get("deleteList") instanceof Collection<?> deleted && !deleted.isEmpty()) {
        List<Object> params = new ArrayList<>(deleted);
        params.add(id);
        Db.update("update def_dict set is_del=1 where id in (" + Values.placeholders(deleted.size()) + ") and parent_id=?", params.toArray());
      }
      Db.update("update def_dict set parent_key=? where parent_id=?", key, id);
    }

    @Override
    public void beforeDelete(Collection<Long> ids) {
      softDelete("def_dict", "parent_id", ids);
    }

    @Override
    public void beforeCopy(Kv source, Row row) {
      row.set("key_", source.getStr("key_") + "_copy").set("classify", DICT_SYSTEM).set("parent_id", ROOT_ID);
      checkKey(row, null);
    }

    @Override
    public void afterCopy(Long sourceId, Row row) {
      for (Kv item : Db.findMaps("select * from def_dict where parent_id=? and is_del=0", sourceId)) {
        Row copy = new Row();
        for (String column : List.of("key_", "name", "state", "remark", "sort_value", "icon", "css_style", "css_class", "classify")) {
          copy.set(column, item.get(column));
        }
        DbRows.save("def_dict", copy.set("id", SnowflakeIdUtils.id()).set("parent_id", row.getLong("id")).set("parent_key", row.getStr("key_")));
      }
    }
  };

  public static final CrudHook DICT_ITEM = new CrudHook() {
    @Override
    public void beforeSave(Map<String, Object> body, Row row) {
      Long parentId = Values.toLong(row.get("parent_id"));
      ChatErrors.require(parentId != null, "字典不能为空");
      ChatErrors.require(row.getStr("key_") != null && !row.getStr("key_").isBlank(), "请填写字典项标识");
      Kv parent = Db.findFirstMap("select key_ from def_dict where id=? and is_del=0", parentId);
      ChatErrors.require(parent != null, "字典不存在");
      ChatErrors.require(!used("def_dict", "key_", row.get("key_"), null, "parent_id=?", parentId), "字典[" + row.get("key_") + "]已经存在，请勿重复创建");
      row.set("parent_key", parent.getStr("key_")).set("classify", DICT_SYSTEM);
    }

    @Override
    public void beforeUpdate(Long id, Map<String, Object> body, Row row) {
      Long parentId = Values.toLong(row.get("parent_id"));
      if (parentId != null) {
        Kv parent = Db.findFirstMap("select key_ from def_dict where id=? and is_del=0", parentId);
        ChatErrors.require(parent != null, "字典不存在");
        row.set("parent_key", parent.getStr("key_"));
      }
    }
  };
}
