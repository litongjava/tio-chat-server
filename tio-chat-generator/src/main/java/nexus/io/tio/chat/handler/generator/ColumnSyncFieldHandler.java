package nexus.io.tio.chat.handler.generator;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenTableService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenTableColumn/syncField 按数据库重新初始化一个字段 */
public class ColumnSyncFieldHandler {

  public HttpResponse handle(HttpRequest request) {
    Long tableId = ParameterValidationUtils.requiredLong(request, "tableId");
    Long id = ParameterValidationUtils.requiredLong(request, "id");
    return ChatResp.ok(Aop.get(GenTableService.class).syncColumn(tableId, id));
  }
}
