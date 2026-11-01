package nexus.io.tio.chat.handler.base.crud;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.chat.model.common.IPageResp;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.chat.service.base.crud.CrudService;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST {path}/export 导出为 CSV 文件 */
public class CrudExportHandler {
  private final CrudDef def;

  public CrudExportHandler(CrudDef def) {
    this.def = def;
  }

  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    body.set("size", 10000).set("current", 1);
    IPageResp<Kv> page = Aop.get(CrudService.class).page(def, body);
    StringBuilder csv = new StringBuilder("﻿");
    boolean header = true;
    for (Kv row : page.getRecords()) {
      row.remove("echoMap");
      if (header) {
        csv.append(String.join(",", row.keySet().stream().map(Object::toString).toList())).append("\n");
        header = false;
      }
      StringBuilder line = new StringBuilder();
      for (Object value : row.values()) {
        if (line.length() > 0) {
          line.append(',');
        }
        String text = value == null ? "" : value instanceof Map ? value.toString() : String.valueOf(value);
        line.append('"').append(text.replace("\"", "\"\"")).append('"');
      }
      csv.append(line).append("\n");
    }
    HttpResponse response = TioRequestContext.getResponse();
    response.setContentType("application/octet-stream");
    response.addHeader("Content-Disposition", "attachment; filename=export.csv");
    response.setBody(csv.toString().getBytes(StandardCharsets.UTF_8));
    return response;
  }
}
