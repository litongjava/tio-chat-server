package nexus.io.tio.chat.handler.oauth.echo;

import java.util.ArrayList;
import java.util.List;

import nexus.io.tio.http.common.HttpRequest;

/** 读取 ids 参数，支持 ids=1&ids=2 和 ids=1,2 */
final class EchoIds {
  private EchoIds() {
  }

  static List<String> of(HttpRequest request) {
    List<String> ids = new ArrayList<>();
    Object[] values = request.getParams() == null ? null : request.getParams().get("ids");
    if (values == null) {
      return ids;
    }
    for (Object value : values) {
      for (String id : String.valueOf(value).split(",")) {
        if (!id.trim().isEmpty()) {
          ids.add(id.trim());
        }
      }
    }
    return ids;
  }
}
