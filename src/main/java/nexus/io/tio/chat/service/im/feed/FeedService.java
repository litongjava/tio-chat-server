package nexus.io.tio.chat.service.im.feed;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.model.common.CursorPageBaseResp;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.service.im.UserSummaryService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.chat.ws.PushService;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 朋友圈：发布、可见范围、点赞、评论与互动通知
 */
public class FeedService {
  public static final String PRIVACY = "privacy";
  public static final String OPEN = "open";
  public static final String NOT_ANYONE = "notAnyone";
  public static final String PART_VISIBLE = "partVisible";
  public static final int MEDIA_WORD = 0;
  public static final int MEDIA_VIDEO = 2;
  public static final int TARGET_TYPE_TAG = 1;
  public static final int TARGET_TYPE_USER = 2;

  private List<Long> friendIds(long uid) {
    return Db.query("select friend_uid from im_user_friend where uid=? and is_del=0", uid);
  }

  // ---------------------------------------------------------------- 发布

  public boolean push(long uid, Kv param) {
    long feedId = SnowflakeIdUtils.id();
    Db.tx(() -> {
      DbRows.save("im_feed", Row.by("id", feedId).set("uid", uid).set("content", param.getStr("content"))
          .set("media_type", Values.toInt(param.get("mediaType"))).set("permission", param.getStr("permission"))
          .set("create_by", uid));
      saveRelations(uid, feedId, param);
      return true;
    });
    pushNewFeed(uid, feedId, param);
    return true;
  }

  public boolean edit(long uid, Kv param) {
    Long feedId = Values.toLong(param.get("id"));
    Kv feed = Db.findFirstMap("select * from im_feed where id=? and is_del=0", feedId);
    ChatErrors.require(feed != null, "请选择一条朋友圈!");
    ChatErrors.require(Objects.equals(feed.getLong("uid"), uid), "只能编辑自己的朋友圈!");
    Db.tx(() -> {
      Db.update("update im_feed set media_type=?, permission=?, content=?, update_by=? where id=?",
          Values.toInt(param.get("mediaType")), param.getStr("permission"), param.getStr("content"), uid, feedId);
      Db.update("delete from im_feed_target where feed_id=?", feedId);
      Db.update("delete from im_feed_media where feed_id=?", feedId);
      saveRelations(uid, feedId, param);
      return true;
    });
    pushNewFeed(uid, feedId, param);
    return true;
  }

  @SuppressWarnings("unchecked")
  private void saveRelations(long uid, long feedId, Kv param) {
    String permission = param.getStr("permission");
    if (NOT_ANYONE.equals(permission) || PART_VISIBLE.equals(permission)) {
      for (Long targetId : longs(param.get("targetIds"))) {
        DbRows.save("im_feed_target", Row.by("feed_id", feedId).set("target_id", targetId).set("type", TARGET_TYPE_TAG)
            .set("create_by", uid));
      }
      for (Long user : longs(param.get("uidList"))) {
        DbRows.save("im_feed_target", Row.by("feed_id", feedId).set("target_id", user).set("type", TARGET_TYPE_USER)
            .set("create_by", uid));
      }
    }
    Integer mediaType = Values.toInt(param.get("mediaType"));
    if (mediaType != null && mediaType != MEDIA_WORD) {
      List<Object> images = mediaUrls(param, mediaType);
      ChatErrors.require(!images.isEmpty(), "请至少上传一条素材!");
      for (int i = 0; i < images.size(); i++) {
        DbRows.save("im_feed_media", Row.by("feed_id", feedId).set("url", String.valueOf(images.get(i))).set("sort", i)
            .set("create_by", uid));
      }
    }
  }

  /**
   * 素材地址取 images；客户端类型定义中的 urls、videoUrl 也兼容
   */
  @SuppressWarnings("unchecked")
  private List<Object> mediaUrls(Kv param, int mediaType) {
    for (String key : new String[] { "images", "urls" }) {
      if (param.get(key) instanceof List && !((List<Object>) param.get(key)).isEmpty()) {
        return (List<Object>) param.get(key);
      }
    }
    String videoUrl = param.getStr("videoUrl");
    if (mediaType == MEDIA_VIDEO && videoUrl != null && !videoUrl.isBlank()) {
      return List.of(videoUrl);
    }
    return List.of();
  }

  private List<Long> longs(Object value) {
    return value instanceof List ? Values.toLongList((List<?>) value) : new ArrayList<>();
  }

  /**
   * 按可见范围通知好友有新动态
   */
  private void pushNewFeed(long uid, long feedId, Kv param) {
    String permission = param.getStr("permission");
    Set<Long> receivers = new HashSet<>();
    TargetService targetService = Aop.get(TargetService.class);
    if (PRIVACY.equals(permission)) {
      receivers.add(uid);
    } else if (PART_VISIBLE.equals(permission)) {
      receivers.addAll(targetService.friendsOfTargets(longs(param.get("targetIds")), uid));
      receivers.addAll(longs(param.get("uidList")));
    } else {
      receivers.addAll(friendIds(uid));
      if (NOT_ANYONE.equals(permission)) {
        receivers.removeAll(targetService.friendsOfTargets(longs(param.get("targetIds")), uid));
        receivers.removeAll(longs(param.get("uidList")));
      }
    }
    receivers.removeAll(Db.query("select friend_uid from im_user_friend where uid=? and hide_my_posts=1 and is_del=0", uid));
    receivers.removeAll(Db.query("select uid from im_user_friend where friend_uid=? and hide_their_posts=1 and is_del=0", uid));
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.FEED_SEND_MSG, Kv.by("uid", uid)), receivers);
  }

  public boolean delete(Long feedId) {
    Db.tx(() -> {
      Db.update("delete from im_feed_media where feed_id=?", feedId);
      Db.update("delete from im_feed_target where feed_id=?", feedId);
      Db.update("update im_feed_comment set is_del=1 where feed_id=?", feedId);
      Db.update("delete from im_feed_like where feed_id=?", feedId);
      Db.update("update im_feed set is_del=1 where id=?", feedId);
      return true;
    });
    return true;
  }

  // ---------------------------------------------------------------- 查询

  public CursorPageBaseResp<Kv> page(long uid, String cursor, int pageSize, String userName) {
    List<Long> uids = friendIds(uid);
    uids.add(uid);
    List<Object> params = new ArrayList<>(uids);
    StringBuilder where = new StringBuilder(" from im_feed where is_del=0 and uid in (" + Values.placeholders(uids.size()) + ")");
    long total = Db.queryLong("select count(*)" + where, params.toArray());
    if (cursor != null && !cursor.isBlank() && !"null".equals(cursor)) {
      where.append(" and create_time<?");
      params.add(LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(cursor)), ZoneId.systemDefault()));
    }
    params.add(pageSize);
    List<Kv> rows = Db.findMaps("select *" + where + " order by create_time desc, id desc limit ?", params.toArray());
    if (rows.isEmpty()) {
      return CursorPageBaseResp.empty();
    }
    LocalDateTime last = Values.time(rows.get(rows.size() - 1).get("create_time"));
    String next = String.valueOf(last.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    List<Kv> visible = new ArrayList<>();
    for (Kv feed : rows) {
      if (visible(feed, uid)) {
        visible.add(feed);
      }
    }
    List<Kv> list = build(visible, uid);
    if (userName != null && !userName.isBlank()) {
      list.removeIf(f -> f.getStr("userName") == null || !f.getStr("userName").contains(userName.trim()));
    }
    return new CursorPageBaseResp<>(next, rows.size() != pageSize, list, total);
  }

  private boolean visible(Kv feed, long uid) {
    if (Objects.equals(feed.getLong("uid"), uid)) {
      return true;
    }
    String permission = feed.getStr("permission");
    if (OPEN.equals(permission)) {
      return true;
    }
    if (PRIVACY.equals(permission)) {
      return false;
    }
    Long hit = Db.queryLong("select count(*) from im_feed_target where feed_id=? and type=? and target_id=? and is_del=0",
        feed.getLong("id"), TARGET_TYPE_USER, uid);
    return PART_VISIBLE.equals(permission) ? hit > 0 : hit == 0;
  }

  public Kv detail(Long feedId, long uid) {
    Kv feed = Db.findFirstMap("select * from im_feed where id=? and is_del=0", feedId);
    if (feed == null) {
      return null;
    }
    return build(List.of(feed), uid).get(0);
  }

  public Kv latestByUser(Long targetUid, long uid) {
    Long feedId = Db.queryLong("select id from im_feed where uid=? and is_del=0 order by create_time desc, id desc limit 1", targetUid);
    return feedId == null ? null : detail(feedId, uid);
  }

  public Kv permission(long uid, Long feedId) {
    Kv feed = detail(feedId, uid);
    ChatErrors.require(feed != null, "请选择朋友圈!");
    ChatErrors.require(Objects.equals(feed.getLong("uid"), uid), "只能查看自己的朋友圈!");
    Kv result = Kv.by("targetIds", null).set("uidList", null);
    String permission = feed.getStr("permission");
    if (PART_VISIBLE.equals(permission) || NOT_ANYONE.equals(permission)) {
      List<Long> tags = new ArrayList<>();
      List<Long> users = new ArrayList<>();
      for (Kv t : Db.findMaps("select type, target_id from im_feed_target where feed_id=? and is_del=0", feedId)) {
        if (Values.toInt(t.get("type")) == TARGET_TYPE_TAG) {
          tags.add(t.getLong("target_id"));
        } else {
          users.add(t.getLong("target_id"));
        }
      }
      result.set("targetIds", tags).set("uidList", users);
    }
    return result;
  }

  private List<Kv> build(List<Kv> feeds, long uid) {
    UserSummaryService summaryService = Aop.get(UserSummaryService.class);
    List<Kv> result = new ArrayList<>();
    for (Kv feed : feeds) {
      long feedId = feed.getLong("id");
      Kv vo = Kv.by("id", feedId).set("uid", feed.getLong("uid")).set("content", feed.getStr("content"))
          .set("permission", feed.getStr("permission")).set("mediaType", Values.toInt(feed.get("media_type")))
          .set("createTime", Values.time(feed.get("create_time"))).set("createBy", feed.getLong("create_by"));
      UserSummary owner = summaryService.get(feed.getLong("uid"));
      if (owner != null) {
        vo.set("userName", owner.getName()).set("userAvatar", owner.getAvatar());
      }
      Integer mediaType = Values.toInt(feed.get("media_type"));
      if (mediaType != null && mediaType != MEDIA_WORD) {
        List<String> urls = Db.query("select url from im_feed_media where feed_id=? and is_del=0 order by sort", feedId);
        if (mediaType == MEDIA_VIDEO && !urls.isEmpty()) {
          // 客户端按 videoUrl 显示视频
          vo.set("videoUrl", urls.get(0));
        } else {
          vo.set("urls", urls);
        }
      }
      List<Kv> likes = likeList(feedId);
      vo.set("likeCount", likes.size()).set("hasLiked", hasLiked(uid, feedId));
      if (!likes.isEmpty()) {
        vo.set("likeList", likes);
      }
      List<Kv> comments = comments(feedId);
      vo.set("commentCount", comments.size());
      if (!comments.isEmpty()) {
        vo.set("commentList", comments);
      }
      result.add(vo);
    }
    return result;
  }

  // ---------------------------------------------------------------- 评论

  public boolean addComment(long uid, Long feedId, Long replyCommentId, Long replyUid, String content) {
    Kv feed = Db.findFirstMap("select * from im_feed where id=? and is_del=0", feedId);
    ChatErrors.require(feed != null, "朋友圈不存在");
    if (replyCommentId != null) {
      ChatErrors.require(Db.findFirstMap("select id from im_feed_comment where id=? and is_del=0", replyCommentId) != null,
          "被回复的评论不存在");
    }
    DbRows.save("im_feed_comment", Row.by("feed_id", feedId).set("uid", uid).set("reply_comment_id", replyCommentId)
        .set("reply_uid", replyUid).set("content", content).set("create_by", uid));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("replyCommentId", replyCommentId);
    data.put("comment", content);
    notifyInteraction(feed, uid, data);
    return true;
  }

  public boolean deleteComment(long uid, Long commentId) {
    Kv comment = Db.findFirstMap("select * from im_feed_comment where id=? and is_del=0", commentId);
    ChatErrors.require(comment != null, "评论不存在");
    ChatErrors.require(Objects.equals(comment.getLong("uid"), uid), "无权删除该评论");
    return Db.update("update im_feed_comment set is_del=1 where id=?", commentId) > 0;
  }

  public List<Kv> comments(Long feedId) {
    return buildComments(Db.findMaps("select * from im_feed_comment where feed_id=? and is_del=0 order by create_time", feedId));
  }

  public CursorPageBaseResp<Kv> commentPage(Long feedId, String cursor, int pageSize) {
    List<Object> params = new ArrayList<>();
    params.add(feedId);
    StringBuilder where = new StringBuilder(" from im_feed_comment where feed_id=? and is_del=0");
    if (cursor != null && !cursor.isBlank() && !"null".equals(cursor)) {
      where.append(" and id<?");
      params.add(Long.parseLong(cursor));
    }
    params.add(pageSize);
    List<Kv> rows = Db.findMaps("select *" + where + " order by id desc limit ?", params.toArray());
    String next = rows.isEmpty() ? null : String.valueOf(rows.get(rows.size() - 1).getLong("id"));
    return new CursorPageBaseResp<>(next, rows.size() != pageSize, buildComments(rows), null);
  }

  public int commentCount(Long feedId) {
    return Db.queryLong("select count(*) from im_feed_comment where feed_id=? and is_del=0", feedId).intValue();
  }

  private List<Kv> buildComments(List<Kv> rows) {
    List<Kv> result = new ArrayList<>();
    if (rows.isEmpty()) {
      return result;
    }
    Set<Long> uids = new HashSet<>();
    for (Kv row : rows) {
      uids.add(row.getLong("uid"));
      if (row.getLong("reply_uid") != null) {
        uids.add(row.getLong("reply_uid"));
      }
    }
    Map<Long, UserSummary> users = Aop.get(UserSummaryService.class).getBatch(uids);
    for (Kv row : rows) {
      Kv vo = Kv.by("id", row.getLong("id")).set("feedId", row.getLong("feed_id")).set("uid", row.getLong("uid"))
          .set("replyCommentId", row.getLong("reply_comment_id")).set("replyUid", row.getLong("reply_uid"))
          .set("content", row.getStr("content")).set("createTime", Values.time(row.get("create_time")))
          .set("createBy", row.getLong("create_by"));
      UserSummary user = users.get(row.getLong("uid"));
      if (user != null) {
        vo.set("userName", user.getName()).set("userAvatar", user.getAvatar());
      }
      UserSummary replyUser = row.getLong("reply_uid") == null ? null : users.get(row.getLong("reply_uid"));
      if (replyUser != null) {
        vo.set("replyUserName", replyUser.getName());
      }
      result.add(vo);
    }
    return result;
  }

  // ---------------------------------------------------------------- 点赞

  /**
   * actType=1 点赞，actType=2 取消点赞
   */
  public boolean like(long uid, Long feedId, Integer actType) {
    Kv feed = Db.findFirstMap("select * from im_feed where id=? and is_del=0", feedId);
    ChatErrors.require(feed != null, "朋友圈不存在");
    Kv old = Db.findFirstMap("select id from im_feed_like where feed_id=? and uid=?", feedId, uid);
    if (Objects.equals(actType, 1)) {
      if (old == null) {
        DbRows.save("im_feed_like", Row.by("feed_id", feedId).set("uid", uid).set("create_by", uid));
        notifyInteraction(feed, uid, new LinkedHashMap<>());
      }
    } else if (Objects.equals(actType, 2)) {
      if (old == null) {
        return true;
      }
      Db.update("delete from im_feed_like where id=?", old.getLong("id"));
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("isUnlike", true);
      notifyInteraction(feed, uid, data);
    } else {
      throw ChatErrors.of("操作类型错误");
    }
    return true;
  }

  public List<Kv> likeList(Long feedId) {
    List<Long> uids = Db.query("select uid from im_feed_like where feed_id=? order by create_time", feedId);
    Map<Long, UserSummary> users = Aop.get(UserSummaryService.class).getBatch(uids);
    List<Kv> result = new ArrayList<>();
    for (Long likeUid : uids) {
      UserSummary user = users.get(likeUid);
      if (user != null) {
        result.add(Kv.by("uid", likeUid).set("userName", user.getName()).set("userAvatar", user.getAvatar()));
      }
    }
    return result;
  }

  public int likeCount(Long feedId) {
    return Db.queryLong("select count(*) from im_feed_like where feed_id=?", feedId).intValue();
  }

  public boolean hasLiked(long uid, Long feedId) {
    return Db.queryLong("select count(*) from im_feed_like where feed_id=? and uid=?", feedId, uid) > 0;
  }

  /**
   * 通知发布者及参与过互动的好友
   */
  private void notifyInteraction(Kv feed, long operator, Map<String, Object> extra) {
    long feedId = feed.getLong("id");
    long owner = feed.getLong("uid");
    Set<Long> receivers = new HashSet<>(Db.query("select uid from im_feed_like where feed_id=?", feedId));
    receivers.addAll(Db.query("select uid from im_feed_comment where feed_id=? and is_del=0", feedId));
    receivers.retainAll(friendIds(owner));
    receivers.add(owner);
    receivers.add(operator);
    Map<String, Object> data = new LinkedHashMap<>(extra);
    data.put("uid", operator);
    data.put("feedId", feedId);
    data.put("feedOwnerUid", owner);
    UserSummary user = Aop.get(UserSummaryService.class).get(operator);
    if (user != null) {
      data.put("operatorName", user.getName());
      data.put("operatorAvatar", user.getAvatar());
    }
    Aop.get(PushService.class).push(WsBaseResp.of(WsRespType.FEED_NOTIFY, data), receivers);
  }
}
