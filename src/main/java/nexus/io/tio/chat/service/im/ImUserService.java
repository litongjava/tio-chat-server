package nexus.io.tio.chat.service.im;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.utils.AccountUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

public class ImUserService {

  public Long findUidByUserId(long userId, long tenantId) {
    return Db.queryLong("select id from im_user where user_id=? and tenant_id=? and is_del=0 limit 1", userId,
        tenantId);
  }

  public Kv getById(long uid) {
    return Db.findFirstMap("select * from im_user where id=? and is_del=0", uid);
  }

  public boolean existsEmail(String email) {
    Long id = Db.queryLong("select id from im_user where email=? and is_del=0 limit 1", email);
    return id != null;
  }

  /**
   * 创建 IM 用户，加入默认群并与系统机器人成为好友
   */
  public long register(long userId, String account, String name, String email, String avatar, Integer sex,
      long tenantId) {
    long uid = SnowflakeIdUtils.id();
    Row user = Row.by("id", uid).set("user_id", userId).set("user_type", ImConst.USER_TYPE_NORMAL).set("name", name)
        .set("avatar", avatar == null ? AccountUtils.defaultAvatar() : avatar).set("email", email == null ? "" : email)
        .set("account", account).set("sex", sex == null ? 0 : sex).set("open_id", "")
        .set("resume", "这个人还没有填写个人简介呢").set("context", 0).set("state", 0).set("tenant_id", tenantId)
        .set("create_by", 1L);
    Db.save("im_user", user);

    ContactService contactService = Aop.get(ContactService.class);
    RoomService roomService = Aop.get(RoomService.class);
    contactService.createContact(uid, ImConst.DEF_ROOM_ID, tenantId);
    roomService.createGroupMember(ImConst.DEF_GROUP_ID, uid, ImConst.GROUP_ROLE_MEMBER, tenantId);

    long friendRoomId = roomService.createFriendRoom(uid, ImConst.DEF_BOT_ID, tenantId);
    Aop.get(FriendService.class).createFriend(friendRoomId, uid, ImConst.DEF_BOT_ID, tenantId);
    return uid;
  }

  public void refreshLoginInfo(long uid, String ip) {
    String ipInfo = "{\"createIp\":\"" + ip + "\",\"updateIp\":\"" + ip + "\"}";
    Db.update("update im_user set last_opt_time=now(3), ip_info=coalesce(ip_info, ?) where id=?", ipInfo, uid);
  }
}
