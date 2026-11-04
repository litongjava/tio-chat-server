package nexus.io.tio.chat.handler.generator;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenCodeService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenTable/previewCode 预览代码 */
public class PreviewCodeHandler {

  public HttpResponse handle(HttpRequest request) {
    Long id = ParameterValidationUtils.requiredLong(request, "id");
    return ChatResp.ok(Aop.get(GenCodeService.class).preview(id, request.getParam("template")));
  }
}
