package nexus.io.tio.chat.handler.im.room;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.NoticeService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/room/notice/read */
public class NoticeReadHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    NoticeService noticeService = Aop.get(NoticeService.class);
    noticeService.markAsRead(Values.toLong(body.get("noticeId")));
    return ChatResp.ok();
  }
}
