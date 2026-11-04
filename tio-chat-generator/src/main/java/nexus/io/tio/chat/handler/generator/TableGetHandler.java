package nexus.io.tio.chat.handler.generator;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenTableService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/generator/defGenTable/{id} 单条表配置 */
public class TableGetHandler {

  public HttpResponse handle(HttpRequest request) {
    Long id = Values.toLong(request.getPathVar("id"));
    ParameterValidationUtils.required(id, "id不能为空");
    return ChatResp.ok(Aop.get(GenTableService.class).get(id));
  }
}
