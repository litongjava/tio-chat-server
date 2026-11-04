package nexus.io.tio.chat.handler.generator;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.model.generator.GenerateReq;
import nexus.io.tio.chat.service.generator.GenCodeService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenTable/generatorCode 生成到本地目录，只有开发环境可用 */
public class GeneratorCodeHandler {

  public HttpResponse handle(HttpRequest request) {
    GenerateReq req = ParameterValidationUtils.parseBody(request, GenerateReq.class);
    return ChatResp.ok(Aop.get(GenCodeService.class).generate(req));
  }
}
