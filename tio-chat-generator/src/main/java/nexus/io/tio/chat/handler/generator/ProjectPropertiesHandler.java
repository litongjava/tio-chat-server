package nexus.io.tio.chat.handler.generator;

import com.jfinal.kit.Kv;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/generator/defGenProject/anno/getProperties 只返回运行环境的版本信息，不返回其他系统属性 */
public class ProjectPropertiesHandler {

  public HttpResponse handle(HttpRequest request) {
    return ChatResp.ok(Kv.by("java.version", System.getProperty("java.version")).set("os.name", System.getProperty("os.name"))
        .set("file.encoding", System.getProperty("file.encoding")));
  }
}
