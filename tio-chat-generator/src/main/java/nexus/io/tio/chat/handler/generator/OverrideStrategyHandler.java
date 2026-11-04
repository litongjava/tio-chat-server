package nexus.io.tio.chat.handler.generator;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenCodeService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/generator/defGenTable/getDefFileOverrideStrategy 默认的文件覆盖策略 */
public class OverrideStrategyHandler {

  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(GenCodeService.defaultStrategies());
  }
}
