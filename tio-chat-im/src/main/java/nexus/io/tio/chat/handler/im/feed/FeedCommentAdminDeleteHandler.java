package nexus.io.tio.chat.handler.im.feed;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.facade.Facades;
import nexus.io.tio.chat.service.im.feed.FeedService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/feed/comment/del：管理员可以删除任何评论，其他用户只能删除自己的 */
public class FeedCommentAdminDeleteHandler {
  public HttpResponse handle(HttpRequest request) {
    Long commentId = Values.toLong(ParameterValidationUtils.bodyKv(request).get("commentId"));
    ParameterValidationUtils.required(commentId, "缺少必须的[Long]类型的参数[commentId]");
    FeedService feeds = Aop.get(FeedService.class);
    long uid = LoginContext.uid();
    return ChatResp.ok(Facades.base().isAdmin(uid) ? feeds.deleteCommentByAdmin(commentId) : feeds.deleteComment(uid, commentId));
  }
}
