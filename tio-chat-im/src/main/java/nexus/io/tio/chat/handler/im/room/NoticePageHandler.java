package nexus.io.tio.chat.handler.im.room;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.NoticeService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/room/notice/page */
public class NoticePageHandler {
  public HttpResponse handle(HttpRequest request) {
    int pageNo = ParameterValidationUtils.optionalInt(request, "pageNo", 1);
    int pageSize = ParameterValidationUtils.optionalInt(request, "pageSize", 10);
    boolean click = "true".equalsIgnoreCase(request.getParam("click"));
    String applyType = request.getParam("applyType", "friend");
    NoticeService noticeService = Aop.get(NoticeService.class);
    return ChatResp.ok(noticeService.page(LoginContext.uid(), pageNo, pageSize, click, applyType));
  }
}
