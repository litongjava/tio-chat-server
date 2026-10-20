package nexus.io.tio.chat.service.oauth;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.utils.ChatErrors;

/**
 * 当前登录账号的资料
 */
public class OauthUserService {

  public Kv getUserInfo(LoginSession session) {
    Kv user = Aop.get(DefUserService.class).getById(session.getUserId());
    if (user == null) {
      throw ChatErrors.of(ChatCode.JWT_USER_INVALID, "用户不存在");
    }
    Kv result = Kv.by("id", user.getLong("id")).set("username", user.getStr("username"))
        .set("nickName", user.getStr("nick_name")).set("email", user.getStr("email"))
        .set("mobile", user.getStr("mobile")).set("idCard", user.getStr("id_card"))
        .set("sex", user.get("sex") == null ? null : user.get("sex").toString()).set("state", toBool(user.get("state")))
        .set("avatar", user.getStr("avatar")).set("workDescribe", user.getStr("work_describe"))
        .set("uid", session.getUid()).set("createTime", user.get("create_time"))
        .set("updateTime", user.get("update_time")).set("baseEmployee", null).set("defApplication", null)
        .set("homePath", null).set("echoMap", Kv.create());
    return result;
  }

  public boolean updateAvatar(long userId, String avatar) {
    Db.update("update def_user set avatar=? where id=?", avatar, userId);
    return true;
  }

  public boolean updateEmail(long userId, String email) {
    Db.update("update def_user set email=? where id=?", email, userId);
    return true;
  }

  public boolean updateMobile(long userId, String mobile) {
    Db.update("update def_user set mobile=? where id=?", mobile, userId);
    return true;
  }

  public boolean updateBaseInfo(long userId, Kv data) {
    String sql = """
        update def_user set nick_name=coalesce(?, nick_name), sex=coalesce(?, sex),
          work_describe=coalesce(?, work_describe), id_card=coalesce(?, id_card)
        where id=?
        """;
    Db.update(sql, data.getStr("nickName"), data.getStr("sex"), data.getStr("workDescribe"), data.getStr("idCard"),
        userId);
    return true;
  }

  private Boolean toBool(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    return "1".equals(value.toString()) || "true".equalsIgnoreCase(value.toString());
  }
}
