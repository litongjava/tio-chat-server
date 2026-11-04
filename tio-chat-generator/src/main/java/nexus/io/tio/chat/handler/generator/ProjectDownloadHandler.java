package nexus.io.tio.chat.handler.generator;

import com.alibaba.fastjson2.JSON;
import java.util.HashMap;
import java.util.Map;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.chat.model.generator.ProjectGenerateReq;
import nexus.io.tio.chat.service.generator.GenProjectService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenProject/download 下载项目，参数为表单字段 */
public class ProjectDownloadHandler {

  public HttpResponse handle(HttpRequest request) {
    Map<String, Object> params = new HashMap<>();
    request.getParams().forEach((name, values) -> {
      if (values != null && values.length > 0) {
        params.put(name, values[0]);
      }
    });
    ProjectGenerateReq req = JSON.parseObject(JSON.toJSONString(params), ProjectGenerateReq.class);
    byte[] zip = Aop.get(GenProjectService.class).download(req);
    HttpResponse response = TioRequestContext.getResponse();
    response.setContentType("application/octet-stream");
    response.addHeader("Content-Disposition", "attachment; filename=project.zip");
    response.setBody(zip);
    return response;
  }
}
