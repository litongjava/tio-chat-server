package nexus.io.tio.chat.config.route;

import nexus.io.tio.chat.handler.oauth.ext.AuthorizeUrlHandler;
import nexus.io.tio.chat.handler.oauth.ext.CheckEmployeeHaveApplicationHandler;
import nexus.io.tio.chat.handler.oauth.ext.EchoFindByIdsHandler;
import nexus.io.tio.chat.handler.oauth.ext.FindCompanyDeptHandler;
import nexus.io.tio.chat.handler.oauth.ext.FindDeptByCompanyHandler;
import nexus.io.tio.chat.handler.oauth.ext.FindDictByTypeHandler;
import nexus.io.tio.chat.handler.oauth.ext.FindDictMapByTypeHandler;
import nexus.io.tio.chat.handler.oauth.ext.FindEnumByTypeHandler;
import nexus.io.tio.chat.handler.oauth.ext.FindEnumMapByTypeHandler;
import nexus.io.tio.chat.handler.oauth.ext.FindParamMapByKeyHandler;
import nexus.io.tio.chat.handler.oauth.ext.FindVisibleResourceHandler;
import nexus.io.tio.chat.handler.oauth.ext.GetPermissionListHandler;
import nexus.io.tio.chat.handler.oauth.ext.GetRoleListHandler;
import nexus.io.tio.chat.handler.oauth.ext.OptLogHandler;
import nexus.io.tio.chat.handler.oauth.ext.SwitchTenantAndOrgHandler;
import nexus.io.tio.chat.handler.oauth.ext.ThirdPartyCallbackHandler;
import nexus.io.tio.chat.handler.oauth.ext.VisibleResourceHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class OauthExtRouteConfig {
  private static final String P = "/api/oauth";

  public void config(HttpRequestRouter r) {
    r.get(P + "/anyTenant/{provider}/authorize-url", new AuthorizeUrlHandler()::handle);
    r.get(P + "/anyTenant/{provider}/callback", new ThirdPartyCallbackHandler()::handle);
    r.post(P + "/anyTenant/enums/findEnumMapByType", new FindEnumMapByTypeHandler()::handle);
    r.post(P + "/anyTenant/enums/findEnumByType", new FindEnumByTypeHandler()::handle);
    r.post(P + "/anyUser/dict/findDictMapByType", new FindDictMapByTypeHandler()::handle);
    r.post(P + "/anyUser/dict/findDictByType", new FindDictByTypeHandler()::handle);
    r.post(P + "/anyUser/parameter/findParamMapByKey", new FindParamMapByKeyHandler()::handle);
    r.get(P + "/anyone/visible/resource", new VisibleResourceHandler()::handle);
    r.get(P + "/anyone/findVisibleResource", new FindVisibleResourceHandler()::handle);
    r.get(P + "/anyone/checkEmployeeHaveApplication", new CheckEmployeeHaveApplicationHandler()::handle);
    r.get(P + "/anyone/getPermissionList", new GetPermissionListHandler()::handle);
    r.get(P + "/anyone/getRoleList", new GetRoleListHandler()::handle);
    r.get(P + "/anyone/findCompanyDept", new FindCompanyDeptHandler()::handle);
    r.get(P + "/anyone/findDeptByCompany", new FindDeptByCompanyHandler()::handle);
    r.put(P + "/anyone/switchTenantAndOrg", new SwitchTenantAndOrgHandler()::handle);
    r.post(P + "/echo/{type}/findByIds", new EchoFindByIdsHandler()::handle);
    r.post(P + "/optLog", new OptLogHandler()::handle);
  }
}
