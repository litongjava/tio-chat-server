package nexus.io.tio.chat.handler.oauth.echo;

import java.util.List;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.oauth.EchoLookupService;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.boot.http.TioRequestContext;

/** POST /api/oauth/echo/dict/findByIds?ids=1&ids=2，与原接口一样直接返回映射 */
public class EchoDictFindByIdsHandler {
  public HttpResponse handle(HttpRequest request) {
    List<String> ids = EchoIds.of(request);
    return TioRequestContext.getResponse().setJson(Aop.get(EchoLookupService.class).dictNames(ids));
  }
}
