package nexus.io.tio.chat.service.base;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.model.common.IPageResp;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.chat.service.base.crud.CrudService;
import nexus.io.tio.chat.service.base.msg.MsgSendService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 后台管理：租户、应用、站内消息、字典与参数、可见资源
 */
public class BaseExtService {
  private static final CrudDef TENANT = CrudDef.of("/defTenant", "def_tenant");
  private static final CrudDef APPLICATION = CrudDef.of("/defApplication", "def_application");
  private static final CrudDef NOTICE = CrudDef.of("/anyone/extendNotice", "extend_notice");
  private static final CrudDef MSG = CrudDef.of("/extendMsg", "extend_msg");

  private CrudService crud() {
    return Aop.get(CrudService.class);
  }

  // ---------------------------------------------------------------- 租户

  public List<Kv> tenants() {
    List<Kv> result = new ArrayList<>();
    for (Kv row : Db.findMaps("select * from def_tenant where is_del=0 and status=0 order by id")) {
      result.add(crud().toVo(TENANT, row));
    }
    return result;
  }

  public boolean updateTenantStatus(Long id, Integer status) {
    return Db.update("update def_tenant set status=? where id=?", String.valueOf(status), id) > 0;
  }

  public boolean updateTenantState(Long id, Boolean state) {
    return Db.update("update def_tenant set state=? where id=?", Values.isTrue(state), id) > 0;
  }

  public List<Kv> tenantsOfUser(Long userId) {
    String sql = """
        select t.* from def_tenant t join def_user_tenant_rel r on r.tenant_id=t.id
        where r.user_id=? and r.is_del=0 and t.is_del=0 order by r.is_default desc
        """;
    List<Kv> result = new ArrayList<>();
    for (Kv row : Db.findMaps(sql, userId)) {
      result.add(crud().toVo(TENANT, row));
    }
    return result;
  }

  public boolean bindTenantUsers(Long tenantId, List<Long> userIds, boolean bind) {
    for (Long userId : userIds) {
      Db.update("delete from def_user_tenant_rel where tenant_id=? and user_id=?", tenantId, userId);
      if (bind) {
        DbRows.save("def_user_tenant_rel", Row.by("tenant_id", tenantId).set("user_id", userId).set("is_default", 0)
            .set("state", 1).set("create_by", operator()));
      }
    }
    return true;
  }

  // ---------------------------------------------------------------- 应用

  public boolean updateDefApp(Long applicationId, Long userId) {
    ChatErrors.require(crud().get(APPLICATION, applicationId) != null, "应用不存在");
    Db.update("delete from def_user_application where user_id=?", userId);
    return DbRows.save("def_user_application", Row.by("user_id", userId).set("application_id", applicationId)
        .set("create_by", userId));
  }

  public Kv defApp(Long userId) {
    Long appId = Db.queryLong("select application_id from def_user_application where user_id=? and is_del=0 limit 1", userId);
    return appId == null ? null : crud().get(APPLICATION, appId);
  }

  public List<Kv> myApplications(String name) {
    String sql = """
        select a.*, r.expiration_time from def_application a
        left join def_tenant_application_rel r on r.application_id=a.id
        where a.is_del=0 and (a.is_general=1 or r.id is not null) and (? is null or a.name like ?)
        order by a.sort_value
        """;
    List<Kv> result = new ArrayList<>();
    for (Kv row : Db.findMaps(sql, name, name == null ? null : "%" + name + "%")) {
      Kv vo = crud().toVo(APPLICATION, row);
      LocalDateTime expiration = Values.time(row.get("expiration_time"));
      vo.set("expirationTime", expiration).set("state", expiration == null || expiration.isAfter(LocalDateTime.now()) ? "1" : "0");
      result.add(vo);
    }
    return result;
  }

  public List<Kv> recommendApplications(String name) {
    String sql = "select * from def_application where is_del=0 and is_visible=1 and (? is null or name like ?) order by sort_value";
    List<Kv> result = new ArrayList<>();
    for (Kv row : Db.findMaps(sql, name, name == null ? null : "%" + name + "%")) {
      result.add(crud().toVo(APPLICATION, row));
    }
    return result;
  }

  // ---------------------------------------------------------------- 站内消息

  /**
   * 我的待办、通知、预警，各自分页
   */
  public Kv myNotice(long current, long size) {
    Long uid = LoginContext.uid();
    Kv result = Kv.create();
    String[][] modes = { { "todoList", "01" }, { "noticeList", "02" }, { "earlyWarningList", "03" } };
    for (String[] mode : modes) {
      long total = Db.queryLong("select count(*) from extend_notice where remind_mode=? and is_read=0 and recipient_id=? and is_del=0",
          mode[1], uid);
      List<Kv> records = new ArrayList<>();
      String sql = "select * from extend_notice where remind_mode=? and is_read=0 and recipient_id=? and is_del=0 order by id desc limit ?, ?";
      for (Kv row : Db.findMaps(sql, mode[1], uid, (current - 1) * size, size)) {
        records.add(crud().toVo(NOTICE, row));
      }
      result.set(mode[0], IPageResp.of(records, total, size, current));
    }
    return result;
  }

  public boolean markNotice(List<Long> noticeIds) {
    if (noticeIds.isEmpty()) {
      return true;
    }
    List<Object> params = new ArrayList<>();
    params.add(LocalDateTime.now());
    params.add(LoginContext.uid());
    params.addAll(noticeIds);
    return Db.update("update extend_notice set is_read=1, read_time=? where recipient_id=? and id in ("
        + Values.placeholders(noticeIds.size()) + ")", params.toArray()) >= 0;
  }

  /** 只删除发给当前用户的通知 */
  public boolean deleteNotice(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return true;
    }
    List<Object> params = new ArrayList<>(ids);
    params.add(LoginContext.uid());
    Db.update("update extend_notice set is_del=1 where id in (" + Values.placeholders(ids.size()) + ") and recipient_id=?", params.toArray());
    return true;
  }

  /**
   * 按模板通过配置的渠道发送
   */
  public boolean sendByTemplate(Map<String, Object> data) {
    return Aop.get(MsgSendService.class).sendByTemplate(data);
  }

  /**
   * 发布站内信，draft=true 只保存草稿，sendTime 到期后才发送
   */
  public boolean publish(Map<String, Object> data) {
    return Aop.get(MsgSendService.class).publish(data);
  }

  public Kv msgDetail(Long id) {
    Kv vo = crud().get(MSG, id);
    if (vo != null) {
      vo.set("recipientList", Db.query("select recipient from extend_msg_recipient where msg_id=? and is_del=0", id));
    }
    return vo;
  }

  @SuppressWarnings("unchecked")
  public boolean batchSaveInterfaceProperty(Map<String, Object> data) {
    CrudDef def = CrudDef.of("/defInterfaceProperty", "def_interface_property");
    for (String key : new String[] { "insertRecords", "updateRecords" }) {
      if (data.get(key) instanceof Collection) {
        for (Object item : (Collection<Object>) data.get(key)) {
          Map<String, Object> map = (Map<String, Object>) item;
          if ("insertRecords".equals(key)) {
            crud().save(def, map);
          } else {
            crud().update(def, map);
          }
        }
      }
    }
    if (data.get("removeRecords") instanceof Collection) {
      List<Long> ids = new ArrayList<>();
      for (Object item : (Collection<Object>) data.get("removeRecords")) {
        ids.add(Values.toLong(((Map<String, Object>) item).get("id")));
      }
      crud().delete(def, ids);
    }
    return true;
  }

  // ---------------------------------------------------------------- 字典与参数

  /**
   * 字典项转下拉选项：{label, text, value, color}
   */
  public Map<String, List<Kv>> dictOptions(List<Map<String, Object>> queries) {
    Map<String, List<Kv>> result = new LinkedHashMap<>();
    for (Map<String, Object> query : queries) {
      String type = String.valueOf(query.get("type"));
      @SuppressWarnings("unchecked")
      List<Object> excludes = query.get("excludes") instanceof List ? (List<Object>) query.get("excludes") : List.of();
      List<Kv> options = new ArrayList<>();
      if (query.get("extend") instanceof Map && Values.isTrue(query.get("extendFirst"))) {
        options.add(Kv.create().set((Map<?, ?>) query.get("extend")));
      }
      String sql = "select key_, name, css_class from def_dict where parent_key=? and is_del=0 and state=1 order by sort_value";
      for (Kv item : Db.findMaps(sql, type)) {
        if (excludes.contains(item.getStr("key_"))) {
          continue;
        }
        options.add(Kv.by("label", item.getStr("name")).set("text", item.getStr("name")).set("value", item.getStr("key_"))
            .set("color", item.getStr("css_class")));
      }
      if (query.get("extend") instanceof Map && !Values.isTrue(query.get("extendFirst"))) {
        options.add(Kv.create().set((Map<?, ?>) query.get("extend")));
      }
      result.put(type, options);
    }
    return result;
  }

  public Map<String, String> params(List<String> keys) {
    Map<String, String> result = new LinkedHashMap<>();
    if (keys.isEmpty()) {
      return result;
    }
    String sql = "select key_, value from def_parameter where is_del=0 and state=1 and key_ in (" + Values.placeholders(keys.size()) + ")";
    for (Kv row : Db.findMaps(sql, keys.toArray())) {
      result.put(row.getStr("key_"), row.getStr("value"));
    }
    return result;
  }

  // ---------------------------------------------------------------- 可见资源

  /**
   * 当前员工可见的菜单路由、资源编码与角色编码
   */
  public Kv visibleResource(Long employeeId, Long applicationId) {
    BaseAdminService admin = Aop.get(BaseAdminService.class);
    List<String> resources = admin.employeeResourceCodes(employeeId, applicationId);
    List<Kv> routers = new ArrayList<>();
    StringBuilder sql = new StringBuilder("select * from def_resource where is_del=0 and state=1 and resource_type in ('20','30')");
    List<Object> params = new ArrayList<>();
    if (applicationId != null) {
      sql.append(" and application_id=?");
      params.add(applicationId);
    }
    sql.append(" order by sort_value");
    for (Kv row : Db.findMaps(sql.toString(), params.toArray())) {
      Kv meta = Kv.by("title", row.getStr("name")).set("icon", row.getStr("icon"))
          .set("hideMenu", Values.toBool(row.get("is_hidden"))).set("resourceType", row.getStr("resource_type"));
      routers.add(Kv.by("id", row.getLong("id")).set("parentId", row.getLong("parent_id")).set("path", row.getStr("path"))
          .set("name", row.getStr("code")).set("component", row.getStr("component")).set("redirect", row.getStr("redirect"))
          .set("meta", meta));
    }
    return Kv.by("enabled", true).set("caseSensitive", false).set("roleList", admin.employeeRoleCodes(employeeId))
        .set("resourceList", resources).set("routerList", admin.buildTree(routers));
  }

  private Long operator() {
    return LoginContext.get() == null ? 0L : LoginContext.get().getUserId();
  }
}
