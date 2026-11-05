package ${handlerPackage};

import java.nio.charset.StandardCharsets;

import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.model.upload.UploadFile;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import ${servicePackage}.${table.entityName}Service;

/**
 * POST ${routePath}/import：导入导出格式的 CSV 文件
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}ImportHandler {

  public HttpResponse handle(HttpRequest request) {
    UploadFile file = request.getUploadFile("file");
    if (file == null || file.getData() == null) {
      return TioRequestContext.getResponse().setJson(RespBodyVo.fail("请选择要导入的文件"));
    }
    String csv = new String(file.getData(), StandardCharsets.UTF_8);
    return TioRequestContext.getResponse().setJson(RespBodyVo.ok(Aop.get(${table.entityName}Service.class).importCsv(csv)));
  }
}
