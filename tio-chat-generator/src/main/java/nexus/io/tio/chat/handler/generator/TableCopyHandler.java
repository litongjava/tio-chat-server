package nexus.io.tio.chat.handler.generator;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenTableService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenTable/copy 复制表配置 */
public class TableCopyHandler {

  public HttpResponse handle(HttpRequest request) {
    Long id = ParameterValidationUtils.optionalLong(request, "id");
    if (id == null) {
      id = Values.toLong(ParameterValidationUtils.bodyKv(request).get("id"));
    }
    ParameterValidationUtils.required(id, "id不能为空");
    return ChatResp.ok(Aop.get(GenTableService.class).copy(id));
  }
}
