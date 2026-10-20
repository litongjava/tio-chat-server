package nexus.io.tio.chat.utils;

import java.util.concurrent.ThreadLocalRandom;

import cn.hutool.crypto.SecureUtil;

/**
 * 账号生成与密码摘要，算法与原系统保持一致，保证已有账号可以直接登录
 */
public class AccountUtils {

  /**
   * 根据 def_user.id 生成 11 位数字账号
   */
  public static String generateAccount(long userId) {
    String md5Hex = SecureUtil.md5(String.valueOf(userId));
    long hashValue = Long.parseLong(md5Hex.substring(0, 10), 16);
    long account = 10000000000L + (hashValue % 10000000000L);
    return String.valueOf(account);
  }

  public static String hashPassword(String password, String salt) {
    return SecureUtil.sha256(password + salt);
  }

  /**
   * 客户端内置 001~022 号默认头像
   */
  public static String defaultAvatar() {
    return String.format("%03d", ThreadLocalRandom.current().nextInt(1, 23));
  }

  public static boolean isEmail(String value) {
    return value != null && value.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
  }
}
