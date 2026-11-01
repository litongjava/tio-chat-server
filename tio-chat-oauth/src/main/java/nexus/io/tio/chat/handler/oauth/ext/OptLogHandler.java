package nexus.io.tio.chat.handler.oauth.ext;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Row;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/oauth/optLog */
public class OptLogHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    Row row = Row.by("request_ip", body.getStr("requestIp")).set("type", body.getStr("type")).set("user_name", body.getStr("userName"))
        .set("description", body.getStr("description")).set("class_path", body.getStr("classPath"))
        .set("action_method", body.getStr("actionMethod")).set("request_uri", body.getStr("requestUri"))
        .set("http_method", body.getStr("httpMethod")).set("consuming_time", body.get("consumingTime")).set("ua", body.getStr("ua"));
    return ChatResp.ok(DbRows.save("base_operation_log", row));
  }
}
