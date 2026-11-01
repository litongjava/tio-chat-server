package nexus.io.tio.chat.handler.oauth.ext;

import nexus.io.tio.chat.facade.Facades;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/oauth/anyone/getPermissionList */
public class GetPermissionListHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Facades.base().employeeResourceCodes(LoginContext.uid(), null));
  }
}
