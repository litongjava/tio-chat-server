package nexus.io.tio.chat.handler.generator;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.model.generator.ProjectGenerateReq;
import nexus.io.tio.chat.service.generator.GenProjectService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenProject/generator 生成项目到本地目录，只有开发环境可用 */
public class ProjectGenerateHandler {

  public HttpResponse handle(HttpRequest request) {
    ProjectGenerateReq req = ParameterValidationUtils.parseBody(request, ProjectGenerateReq.class);
    return ChatResp.ok(Aop.get(GenProjectService.class).generate(req));
  }
}
