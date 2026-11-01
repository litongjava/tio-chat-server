package nexus.io.tio.chat.config.route;

import nexus.io.tio.chat.handler.im.feed.FeedCommentAddHandler;
import nexus.io.tio.chat.handler.im.feed.FeedCommentAllHandler;
import nexus.io.tio.chat.handler.im.feed.FeedCommentCountHandler;
import nexus.io.tio.chat.handler.im.feed.FeedCommentDeleteHandler;
import nexus.io.tio.chat.handler.im.feed.FeedCommentListHandler;
import nexus.io.tio.chat.handler.im.feed.FeedDeleteHandler;
import nexus.io.tio.chat.handler.im.feed.FeedDetailHandler;
import nexus.io.tio.chat.handler.im.feed.FeedEditHandler;
import nexus.io.tio.chat.handler.im.feed.FeedHasLikedHandler;
import nexus.io.tio.chat.handler.im.feed.FeedLatestByUserHandler;
import nexus.io.tio.chat.handler.im.feed.FeedLikeCountHandler;
import nexus.io.tio.chat.handler.im.feed.FeedLikeListHandler;
import nexus.io.tio.chat.handler.im.feed.FeedLikeToggleHandler;
import nexus.io.tio.chat.handler.im.feed.FeedListHandler;
import nexus.io.tio.chat.handler.im.feed.FeedPermissionHandler;
import nexus.io.tio.chat.handler.im.feed.FeedPushHandler;
import nexus.io.tio.chat.handler.im.feed.TargetAddHandler;
import nexus.io.tio.chat.handler.im.feed.TargetDeleteHandler;
import nexus.io.tio.chat.handler.im.feed.TargetDetailHandler;
import nexus.io.tio.chat.handler.im.feed.TargetEditHandler;
import nexus.io.tio.chat.handler.im.feed.TargetRelDetailHandler;
import nexus.io.tio.chat.handler.im.feed.TargetRelEditHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class ImFeedRouteConfig {
  private static final String P = "/api/im";

  public void config(HttpRequestRouter r) {
    r.post(P + "/feed/list", new FeedListHandler()::handle);
    r.post(P + "/feed/pushFeed", new FeedPushHandler()::handle);
    r.get(P + "/feed/getFeedPermission", new FeedPermissionHandler()::handle);
    r.post(P + "/feed/edit", new FeedEditHandler()::handle);
    r.post(P + "/feed/del", new FeedDeleteHandler()::handle);
    r.get(P + "/feed/detail", new FeedDetailHandler()::handle);
    r.get(P + "/feed/latestByUser", new FeedLatestByUserHandler()::handle);
    r.post(P + "/feed/comment/add", new FeedCommentAddHandler()::handle);
    r.post(P + "/feed/comment/delete", new FeedCommentDeleteHandler()::handle);
    r.post(P + "/feed/comment/list", new FeedCommentListHandler()::handle);
    r.get(P + "/feed/comment/count", new FeedCommentCountHandler()::handle);
    r.get(P + "/feed/comment/all", new FeedCommentAllHandler()::handle);
    r.post(P + "/feed/like/toggle", new FeedLikeToggleHandler()::handle);
    r.get(P + "/feed/like/list", new FeedLikeListHandler()::handle);
    r.get(P + "/feed/like/count", new FeedLikeCountHandler()::handle);
    r.get(P + "/feed/like/hasLiked", new FeedHasLikedHandler()::handle);
    r.post(P + "/target/add", new TargetAddHandler()::handle);
    r.post(P + "/target/edit", new TargetEditHandler()::handle);
    r.get(P + "/target/detail", new TargetDetailHandler()::handle);
    r.post(P + "/target/del", new TargetDeleteHandler()::handle);
    r.get(P + "/target/rel/detail", new TargetRelDetailHandler()::handle);
    r.post(P + "/target/rel/edit", new TargetRelEditHandler()::handle);
  }
}
