package nexus.io.tio.chat.handler.base;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.chat.service.base.BaseAdminService;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.json.JsonUtils;

/** GET /api/base/defArea/download */
public class AreaDownloadHandler {
  public HttpResponse handle(HttpRequest request) {
    int treeGrade = ParameterValidationUtils.optionalInt(request, "treeGrade", 2);
    CrudDef def = CrudDef.of("/defArea", "def_area");
    List<Kv> tree = Aop.get(BaseAdminService.class).tree(def, Kv.create());
    String json = JsonUtils.toJson(prune(tree, treeGrade, 0));
    HttpResponse response = TioRequestContext.getResponse();
    response.setContentType("application/octet-stream");
    response.addHeader("Content-Disposition", "attachment; filename=area.json");
    response.setBody(json.getBytes(StandardCharsets.UTF_8));
    return response;
  }

  @SuppressWarnings("unchecked")
  private List<Kv> prune(List<Kv> nodes, int maxGrade, int grade) {
    for (Kv node : nodes) {
      if (grade >= maxGrade) {
        node.remove("children");
      } else if (node.get("children") instanceof List) {
        prune((List<Kv>) node.get("children"), maxGrade, grade + 1);
      }
    }
    return nodes;
  }
}
