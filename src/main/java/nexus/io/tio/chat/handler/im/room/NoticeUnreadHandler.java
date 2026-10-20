package nexus.io.tio.chat.handler.im.room;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.NoticeService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/room/notice/unread */
public class NoticeUnreadHandler {
  public HttpResponse handle(HttpRequest request) {
    long uid = LoginContext.uid();
    NoticeService noticeService = Aop.get(NoticeService.class);
    return ChatResp.ok(noticeService.unread(uid, uid));
  }
}
