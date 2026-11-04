package nexus.io.tio.chat.handler.generator;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.model.generator.ImportTableReq;
import nexus.io.tio.chat.service.generator.GenTableService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenTable/importTable 导入表 */
public class ImportTableHandler {

  public HttpResponse handle(HttpRequest request) {
    ImportTableReq req = ParameterValidationUtils.parseBody(request, ImportTableReq.class);
    return ChatResp.ok(Aop.get(GenTableService.class).importTable(req.getDsId(), req.getTableNames()));
  }
}
