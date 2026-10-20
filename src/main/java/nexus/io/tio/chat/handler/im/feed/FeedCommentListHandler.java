package nexus.io.tio.chat.handler.im.feed;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.feed.FeedService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/feed/comment/list */
public class FeedCommentListHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    int pageSize = body.getInt("pageSize") == null ? 10 : body.getInt("pageSize");
    FeedService feedService = Aop.get(FeedService.class);
    return ChatResp.ok(feedService.commentPage(Values.toLong(body.get("feedId")), body.getStr("cursor"), pageSize));
  }
}
