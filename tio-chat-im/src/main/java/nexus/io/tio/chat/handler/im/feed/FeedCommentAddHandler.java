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

/** POST /api/im/feed/comment/add */
public class FeedCommentAddHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    ParameterValidationUtils.notBlank(body.getStr("content"), "评论内容不能为空");
    FeedService feedService = Aop.get(FeedService.class);
    return ChatResp.ok(feedService.addComment(LoginContext.uid(), Values.toLong(body.get("feedId")),
        Values.toLong(body.get("replyCommentId")), Values.toLong(body.get("replyUid")), body.getStr("content")));
  }
}
