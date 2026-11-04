package nexus.io.tio.chat.handler.generator;

import java.util.ArrayList;
import java.util.List;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.chat.service.generator.GenCodeService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/generator/defGenTable/downloadZip 打包下载 */
public class DownloadZipHandler {

  public HttpResponse handle(HttpRequest request) {
    List<Long> ids = new ArrayList<>();
    Object[] values = request.getParamArray("ids");
    if (values != null) {
      for (Object value : values) {
        for (String id : value.toString().split(",")) {
          if (!id.isEmpty()) {
            ids.add(Values.toLong(id));
          }
        }
      }
    }
    byte[] zip = Aop.get(GenCodeService.class).zip(ids, request.getParam("template"));
    HttpResponse response = TioRequestContext.getResponse();
    response.setContentType("application/octet-stream");
    response.addHeader("Content-Disposition", "attachment; filename=code.zip");
    response.setBody(zip);
    return response;
  }
}
