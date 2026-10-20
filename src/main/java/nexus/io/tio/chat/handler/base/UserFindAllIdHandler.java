package nexus.io.tio.chat.handler.base;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/defUser/findAllUserId */
public class UserFindAllIdHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Db.query("select id from def_user where is_del=0"));
  }
}
