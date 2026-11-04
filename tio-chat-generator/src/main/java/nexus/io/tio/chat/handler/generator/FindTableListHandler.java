package nexus.io.tio.chat.handler.generator;

import com.alibaba.fastjson2.JSON;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenTableService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenTable/findTableList 按 id 查询表配置 */
public class FindTableListHandler {

  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Aop.get(GenTableService.class).findByIds(JSON.parseArray(request.getBodyString(), Long.class)));
  }
}
