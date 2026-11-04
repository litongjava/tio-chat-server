package nexus.io.tio.chat.handler.generator;

import com.alibaba.fastjson2.JSON;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenTableService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** DELETE /api/generator/defGenTable 删除表配置和字段 */
public class TableDeleteHandler {

  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(GenTableService.class).delete(JSON.parseArray(request.getBodyString(), Long.class)));
  }
}
