package nexus.io.tio.chat.handler.generator;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenCodeService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/generator/defGenTable/getFieldTemplate 字段对应的模板 */
public class FieldTemplateHandler {

  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(GenCodeService.fieldTemplates());
  }
}
