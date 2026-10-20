package nexus.io.tio.chat.handler.im.misc;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.ImExtraService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/user/emoji/list */
public class EmojiListHandler {
  public HttpResponse handle(HttpRequest request) {
    ImExtraService service = Aop.get(ImExtraService.class);
    return ChatResp.ok(service.emojis(LoginContext.uid()));
  }
}
