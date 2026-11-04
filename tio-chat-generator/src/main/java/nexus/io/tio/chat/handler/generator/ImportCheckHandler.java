package nexus.io.tio.chat.handler.generator;

import com.alibaba.fastjson2.JSON;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenTableService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenTable/importCheck 导入前检查表是否已经导入 */
public class ImportCheckHandler {

  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(GenTableService.class).importCheck(JSON.parseArray(request.getBodyString(), String.class)));
  }
}
