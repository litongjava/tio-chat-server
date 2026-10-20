package nexus.io.tio.chat.config.route;

import java.util.ArrayList;
import java.util.List;

import nexus.io.tio.chat.service.base.crud.CrudDef;

/**
 * 后台管理的通用增删改查资源，路径与原后台接口一致（统一挂在 /api/base 下）
 */
public class BaseCrudRegistry {

  private static final List<CrudDef> DEFS = build();

  public static List<CrudDef> defs() {
    return DEFS;
  }

  public static CrudDef find(String path) {
    for (CrudDef def : DEFS) {
      if (def.getPath().equals(path)) {
        return def;
      }
    }
    return null;
  }

  private static List<CrudDef> build() {
    List<CrudDef> defs = new ArrayList<>();
    defs.add(CrudDef.of("/defApplication", "def_application").like("app_key", "app_secret", "name", "version", "type", "introduce", "redirect", "remark", "url").echo("type", "dict:TENANT_APPLICATION_TYPE").cache());
    defs.add(CrudDef.of("/defResource", "def_resource").like("code", "name", "resource_type", "describe_", "path", "open_with", "component", "redirect", "icon", "sub_group", "data_scope", "custom_class", "meta_json", "tree_path").echo("resourceType", "dict:TENANT_RESOURCE_TYPE").echo("openWith", "dict:TENANT_RESOURCE_OPEN_WITH").echo("dataScope", "dict:TENANT_RESOURCE_DATA_SCOPE"));
    defs.add(CrudDef.of("/baseLoginLog", "def_login_log").like("request_ip", "nick_name", "username", "description", "login_date", "ua", "browser", "browser_version", "operating_system", "location", "status").echo("status", "dict:SYSTEM_LOGIN_STATUS"));
    defs.add(CrudDef.of("/baseOperationLog", "base_operation_log").like("request_ip", "user_name", "description", "class_path", "action_method", "request_uri", "ua"));
    defs.add(CrudDef.of("/baseRole", "base_role").like("type_", "category", "name", "code", "remarks").echo("category", "dict:BASE_ROLE_CATEGORY").cache());
    defs.add(CrudDef.of("/defArea", "def_area").like("code", "division_code", "full_name", "longitude", "latitude", "level_", "source_", "tree_path", "name").echo("level", "dict:GLOBAL_AREA_LEVEL").echo("source", "dict:TENANT_AREA_SOURCE"));
    defs.add(CrudDef.of("/defClient", "def_client").like("client_id", "client_secret", "name", "type", "remarks").echo("type", "dict:TENANT_CLIENT_TYPE"));
    defs.add(CrudDef.of("/defDict", "def_dict").like("parent_key", "classify", "key_", "name", "remark", "icon", "css_style", "css_class"));
    defs.add(CrudDef.of("/defDictItem", "def_dict").like("parent_key", "classify", "key_", "name", "remark", "icon", "css_style", "css_class"));
    defs.add(CrudDef.of("/defLoginLog", "def_login_log").like("request_ip", "nick_name", "username", "description", "login_date", "ua", "browser", "browser_version", "operating_system", "location", "status").echo("status", "dict:SYSTEM_LOGIN_STATUS"));
    defs.add(CrudDef.of("/defParameter", "def_parameter").like("key_", "value", "name", "remarks", "param_type").cache());
    defs.add(CrudDef.of("/anyone/defTenant", "def_tenant").echo("status", "dict:TENANT_TENANT_STATUS").cache());
    defs.add(CrudDef.of("/defTenant", "def_tenant").echo("status", "dict:TENANT_TENANT_STATUS").cache());
    defs.add(CrudDef.of("/defUser", "def_user").like("username", "nick_name", "avatar", "email", "mobile", "id_card", "work_describe").hidden("password", "salt").echo("sex", "dict:GLOBAL_SEX").echo("nation", "dict:GLOBAL_NATION").echo("education", "dict:GLOBAL_EDUCATION").cache().excel());
    defs.add(CrudDef.of("/baseEmployee", "base_employee").like("name", "position_status", "active_status").hidden("password").echo("positionId", "position").echo("orgIdList", "org").echo("lastCompanyId", "org").echo("positionStatus", "dict:BASE_POSITION_STATUS").cache());
    defs.add(CrudDef.of("/baseOrg", "base_org").like("name", "type_", "short_name", "tree_path", "remarks").echo("type", "dict:BASE_ORG_TYPE").cache());
    defs.add(CrudDef.of("/basePosition", "base_position").like("name", "remarks").echo("orgId", "org"));
    defs.add(CrudDef.of("/defInterface", "def_interface").like("code", "name", "exec_mode", "script", "impl_class").echo("execMode", "dict:INTERFACE_EXEC_MODE"));
    defs.add(CrudDef.of("/defInterfaceProperty", "def_interface_property").like("name", "key_", "value", "remarks"));
    defs.add(CrudDef.of("/defMsgTemplate", "def_msg_template").like("type", "code", "name", "title", "content", "script", "sign", "param", "remarks", "target_", "remind_mode", "url", "template_code").echo("type", "dict:MSG_TEMPLATE_TYPE").echo("target", "dict:NOTICE_TARGET").echo("remindMode", "dict:NOTICE_REMIND_MODE"));
    defs.add(CrudDef.of("/extendInterfaceLog", "extend_interface_log").like("name"));
    defs.add(CrudDef.of("/extendInterfaceLogging", "extend_interface_logging").like("status", "params", "result", "error_msg").echo("status", "dict:MSG_INTERFACE_LOGGING_STATUS"));
    defs.add(CrudDef.of("/extendMsg", "extend_msg").like("type", "param", "title", "content", "biz_type", "author", "remind_mode").echo("type", "dict:MSG_TEMPLATE_TYPE").echo("remindMode", "dict:NOTICE_REMIND_MODE").customGet());
    defs.add(CrudDef.of("/anyone/extendNotice", "extend_notice").like("msg_id", "biz_id", "biz_type", "title", "content", "author", "url").echo("remindMode", "dict:NOTICE_REMIND_MODE").echo("target", "dict:NOTICE_TARGET"));
    return defs;
  }
}
