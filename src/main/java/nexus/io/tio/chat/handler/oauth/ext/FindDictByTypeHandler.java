package nexus.io.tio.chat.handler.oauth.ext;

import nexus.io.tio.chat.facade.Facades;
import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/oauth/anyUser/dict/findDictByType */
public class FindDictByTypeHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    return ChatResp.ok(Facades.base().dictOptions(List.of(body)).get(body.getStr("type")));
  }
}
