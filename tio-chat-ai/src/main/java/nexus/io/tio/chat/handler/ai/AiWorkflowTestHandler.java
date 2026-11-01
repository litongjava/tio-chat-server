package nexus.io.tio.chat.handler.ai;

import com.jfinal.kit.Kv;

import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/ai/workflow/test */
public class AiWorkflowTestHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    return ChatResp.ok(Kv.by("params", body.get("params")).set("graph", body.get("graph")));
  }
}
