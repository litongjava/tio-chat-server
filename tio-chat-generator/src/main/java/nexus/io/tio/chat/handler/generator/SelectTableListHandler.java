package nexus.io.tio.chat.handler.generator;

import com.jfinal.kit.Kv;
import java.util.Map;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.generator.GenTableService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenTable/selectTableList 数据源中的表 */
public class SelectTableListHandler {

  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    Object model = body.get("model");
    Long dsId = model instanceof Map<?, ?> map ? Values.toLong(map.get("dsId")) : null;
    return ChatResp.ok(Aop.get(GenTableService.class).selectTableList(dsId));
  }
}
