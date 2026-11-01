package nexus.io.tio.chat.handler.im.misc;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.ImExtraService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** DELETE /api/im/user/emoji */
public class EmojiDeleteHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    ImExtraService service = Aop.get(ImExtraService.class);
    service.removeEmoji(LoginContext.uid(), Values.toLong(body.get("id")));
    return ChatResp.ok();
  }
}
