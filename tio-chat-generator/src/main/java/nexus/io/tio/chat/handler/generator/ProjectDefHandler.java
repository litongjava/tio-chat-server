package nexus.io.tio.chat.handler.generator;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenProjectService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenProject/getDef 生成项目的默认值 */
public class ProjectDefHandler {

  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(GenProjectService.class).getDef());
  }
}
