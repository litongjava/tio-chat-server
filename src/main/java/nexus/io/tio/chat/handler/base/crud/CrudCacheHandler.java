package nexus.io.tio.chat.handler.base.crud;

import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST {path}/refreshCache、{path}/clearCache：数据直接读库，无需维护缓存 */
public class CrudCacheHandler {
  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(true);
  }
}
