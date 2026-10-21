package nexus.io.tio.chat.handler.im.chat;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.chat.MessageSearchService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/**
 * GET /api/im/chat/msg/files
 * 参数：roomId、senderUid、keyword、types（默认文件与视频）、pageNo、pageSize
 */
public class MsgFilesHandler {
  public HttpResponse handle(HttpRequest request) {
    int pageNo = Math.max(1, ParameterValidationUtils.optionalInt(request, "pageNo", 1));
    int pageSize = Math.min(100, Math.max(1, ParameterValidationUtils.optionalInt(request, "pageSize", 20)));
    MessageSearchService service = Aop.get(MessageSearchService.class);
    return ChatResp.ok(service.files(LoginContext.uid(), ParameterValidationUtils.optionalLong(request, "roomId"),
        ParameterValidationUtils.optionalLong(request, "senderUid"), request.getParam("keyword"),
        MsgSearchHandler.parseTypes(request.getParam("types")), pageNo, pageSize));
  }
}
