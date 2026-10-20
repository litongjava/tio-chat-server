package nexus.io.tio.chat.handler.im.feed;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.feed.FeedService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/feed/edit */
public class FeedEditHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    ParameterValidationUtils.required(Values.toLong(body.get("id")), "id不能为空");
    FeedService feedService = Aop.get(FeedService.class);
    return ChatResp.ok(feedService.edit(LoginContext.uid(), body));
  }
}
