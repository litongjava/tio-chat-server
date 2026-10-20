package nexus.io.tio.chat.service.im;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

public class FriendService {

  /**
   * 建立双向好友关系并创建双方会话
   */
  public void createFriend(long roomId, long uid, long friendUid, long tenantId) {
    saveFriend(roomId, uid, friendUid, tenantId);
    saveFriend(roomId, friendUid, uid, tenantId);
    ContactService contactService = Aop.get(ContactService.class);
    contactService.createContact(uid, roomId, tenantId);
    contactService.createContact(friendUid, roomId, tenantId);
  }

  private void saveFriend(long roomId, long uid, long friendUid, long tenantId) {
    Long exists = Db.queryLong("select id from im_user_friend where uid=? and friend_uid=? and is_del=0", uid,
        friendUid);
    if (exists != null) {
      Db.update("update im_user_friend set room_id=? where id=?", roomId, exists);
      return;
    }
    Row friend = Row.by("id", SnowflakeIdUtils.id()).set("room_id", roomId).set("uid", uid)
        .set("friend_uid", friendUid).set("is_temp", 0).set("tenant_id", tenantId).set("create_by", uid);
    Db.save("im_user_friend", friend);
  }

  public boolean isFriend(long uid, long friendUid) {
    Long id = Db.queryLong("select id from im_user_friend where uid=? and friend_uid=? and is_del=0 limit 1", uid,
        friendUid);
    return id != null;
  }
}
