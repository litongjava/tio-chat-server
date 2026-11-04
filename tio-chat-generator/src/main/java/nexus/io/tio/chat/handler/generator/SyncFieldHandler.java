package nexus.io.tio.chat.handler.generator;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenTableService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenTable/syncField 同步表结构 */
public class SyncFieldHandler {

  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(GenTableService.class).syncField(ParameterValidationUtils.requiredLong(request, "id")));
  }
}
