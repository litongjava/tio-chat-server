package nexus.io.tio.chat.handler.im.room;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.ApplyService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** DELETE /api/im/room/apply/delete */
public class DeleteApplyHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    ApplyService applyService = Aop.get(ApplyService.class);
    applyService.delete(LoginContext.uid(), Values.toLong(body.get("applyId")));
    return ChatResp.ok(true);
  }
}
