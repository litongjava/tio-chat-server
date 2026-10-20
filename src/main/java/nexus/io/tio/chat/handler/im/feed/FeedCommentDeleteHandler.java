package nexus.io.tio.chat.handler.im.feed;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.feed.FeedService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/feed/comment/delete */
public class FeedCommentDeleteHandler {
  public HttpResponse handle(HttpRequest request) {
    Long commentId = ParameterValidationUtils.optionalLong(request, "commentId");
    if (commentId == null) {
      commentId = Values.toLong(ParameterValidationUtils.bodyKv(request).get("commentId"));
    }
    ParameterValidationUtils.required(commentId, "缺少必须的[Long]类型的参数[commentId]");
    FeedService feedService = Aop.get(FeedService.class);
    return ChatResp.ok(feedService.deleteComment(LoginContext.uid(), commentId));
  }
}
