package ${handlerPackage};

import java.util.List;

import com.alibaba.fastjson2.JSON;

import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import ${servicePackage}.${table.entityName}Service;

/**
 * DELETE ${routePath}
 *
 * @author ${author}
 * @date ${datetime}
 */
public class ${table.entityName}DeleteHandler {

  public HttpResponse handle(HttpRequest request) {
    List<Long> ids = JSON.parseArray(request.getBodyString(), Long.class);
    if (ids == null || ids.isEmpty()) {
      return TioRequestContext.getResponse().setJson(RespBodyVo.fail("请选择要删除的数据"));
    }
    return TioRequestContext.getResponse().setJson(RespBodyVo.ok(Aop.get(${table.entityName}Service.class).delete(ids)));
  }
}
