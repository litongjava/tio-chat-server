package nexus.io.tio.chat.handler.base;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/base/defUser/updateState */
public class UserUpdateStateHandler {
  public HttpResponse handle(HttpRequest request) {
    Long id = ParameterValidationUtils.requiredLong(request, "id");
    boolean state = "true".equalsIgnoreCase(request.getParam("state"));
    return ChatResp.ok(Db.update("update def_user set state=? where id=?", state, id) > 0);
  }
}
