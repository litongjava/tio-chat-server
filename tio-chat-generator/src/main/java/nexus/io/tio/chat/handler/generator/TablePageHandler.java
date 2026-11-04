package nexus.io.tio.chat.handler.generator;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenTableService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenTable/page 分页查询表配置 */
public class TablePageHandler {

  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(GenTableService.class).page(ParameterValidationUtils.bodyKv(request)));
  }
}
