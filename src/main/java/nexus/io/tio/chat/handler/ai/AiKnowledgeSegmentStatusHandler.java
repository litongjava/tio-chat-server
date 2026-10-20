package nexus.io.tio.chat.handler.ai;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/ai/knowledge/segment/update-status */
public class AiKnowledgeSegmentStatusHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    return ChatResp.ok(Db.update("update ai_knowledge_segment set status=? where id=?", Values.toInt(body.get("status")), Values.toLong(body.get("id"))) > 0);
  }
}
