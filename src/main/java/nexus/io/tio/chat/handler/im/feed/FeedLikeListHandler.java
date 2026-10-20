package nexus.io.tio.chat.handler.im.feed;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.feed.FeedService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/feed/like/list */
public class FeedLikeListHandler {
  public HttpResponse handle(HttpRequest request) {
    Long feedId = ParameterValidationUtils.requiredLong(request, "feedId");
    FeedService feedService = Aop.get(FeedService.class);
    return ChatResp.ok(feedService.likeList(feedId));
  }
}
