package nexus.io.tio.chat.service.oauth;

import java.time.LocalDateTime;

import com.jfinal.kit.Kv;

import cn.hutool.core.util.RandomUtil;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.tio.chat.utils.AccountUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * def_user 账号表操作
 */
public class DefUserService {

  public Kv getById(long id) {
    return Db.findFirstMap("select * from def_user where id=? and is_del=0", id);
  }

  public Kv getByUsername(Integer systemType, String username) {
    String sql = """
        select * from def_user where username=? and is_del=0 and (? is null or system_type=?) limit 1
        """;
    return Db.findFirstMap(sql, username, systemType, systemType);
  }

  public Kv getByEmail(Integer systemType, String email) {
    String sql = """
        select * from def_user where email=? and is_del=0 and (? is null or system_type=?) limit 1
        """;
    return Db.findFirstMap(sql, email, systemType, systemType);
  }

  public Kv getByMobile(Integer systemType, String mobile) {
    String sql = """
        select * from def_user where mobile=? and is_del=0 and (? is null or system_type=?) limit 1
        """;
    return Db.findFirstMap(sql, mobile, systemType, systemType);
  }

  public boolean existsEmail(String email) {
    return Db.queryLong("select id from def_user where email=? and is_del=0 limit 1", email) != null;
  }

  public boolean existsMobile(String mobile) {
    return Db.queryLong("select id from def_user where mobile=? and is_del=0 limit 1", mobile) != null;
  }

  /**
   * 创建账号，返回 def_user 记录
   */
  public Kv create(Integer systemType, String email, String mobile, String password, String nickName, String avatar,
      Integer sex, long tenantId) {
    long id = SnowflakeIdUtils.id();
    String salt = RandomUtil.randomString(20);
    String account = AccountUtils.generateAccount(id);
    if (avatar == null || avatar.isBlank()) {
      avatar = AccountUtils.defaultAvatar();
    }
    if (nickName == null || nickName.isBlank()) {
      nickName = mobile != null ? mobile : "用户_" + account.substring(account.length() - 6);
    }
    Row user = Row.by("id", id).set("system_type", systemType).set("username", account).set("nick_name", nickName)
        .set("email", email == null ? "" : email).set("mobile", mobile == null ? "" : mobile).set("avatar", avatar).set("sex", sex == null ? 0 : sex).set("state", 1)
        .set("readonly", 0).set("password", AccountUtils.hashPassword(password, salt)).set("salt", salt)
        .set("password_error_num", 0).set("tenant_id", tenantId).set("create_by", id)
        .set("create_time", LocalDateTime.now());
    Db.save("def_user", user);
    return getById(id);
  }

  public void updatePassword(long id, String password, String salt) {
    if (salt == null || salt.isBlank()) {
      salt = RandomUtil.randomString(20);
    }
    String sql = """
        update def_user set password=?, salt=?, password_error_num=0, password_error_last_time=null
        where id=?
        """;
    Db.update(sql, AccountUtils.hashPassword(password, salt), salt, id);
  }

  public void loginSuccess(long id, String ip) {
    String sql = """
        update def_user set last_login_time=now(), password_error_num=0, ip_info=? where id=?
        """;
    Db.update(sql, "{\"updateIp\":\"" + ip + "\"}", id);
  }

  public void passwordError(long id) {
    String sql = """
        update def_user set password_error_num=ifnull(password_error_num,0)+1, password_error_last_time=now()
        where id=?
        """;
    Db.update(sql, id);
  }
}
