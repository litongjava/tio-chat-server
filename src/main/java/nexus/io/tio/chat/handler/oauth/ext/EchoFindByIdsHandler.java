package nexus.io.tio.chat.handler.oauth.ext;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/oauth/echo/{type}/findByIds */
public class EchoFindByIdsHandler {
  public HttpResponse handle(HttpRequest request) {
    String type = request.getPathVar("type");
    String table = switch (type) {
    case "user" -> "def_user";
    case "position" -> "base_position";
    case "org" -> "base_org";
    default -> "def_dict";
    };
    String column = "user".equals(type) ? "nick_name" : "name";
    Map<String, Object> result = new LinkedHashMap<>();
    String ids = request.getParam("ids");
    if (ids != null) {
      for (String id : ids.split(",")) {
        Kv row = Db.findFirstMap("select id, " + column + " as name from " + table + " where id=?", id.trim());
        if (row != null) {
          result.put(id.trim(), row);
        }
      }
    }
    return TioRequestContext.getResponse().setJson(result);
  }
}
