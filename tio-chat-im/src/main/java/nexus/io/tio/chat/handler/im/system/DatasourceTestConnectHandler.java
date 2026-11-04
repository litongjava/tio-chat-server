package nexus.io.tio.chat.handler.im.system;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.DatasourceConfigService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/defDatasourceConfig/testConnect?id=1 */
public class DatasourceTestConnectHandler {
  public HttpResponse handle(HttpRequest request) {
    Long id = ParameterValidationUtils.requiredLong(request, "id");
    return ChatResp.ok(Aop.get(DatasourceConfigService.class).testConnection(id));
  }
}
