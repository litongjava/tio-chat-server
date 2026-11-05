package nexus.io.tio.chat.utils;

import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import nexus.io.db.activerecord.Row;

/**
 * 从请求的 User-Agent 解析登录日志要记录的浏览器和操作系统
 */
public final class UserAgents {

  private UserAgents() {
  }

  public static void fill(Row row, String ua) {
    row.set("ua", ua);
    if (ua == null || ua.isBlank()) {
      return;
    }
    UserAgent agent = UserAgentUtil.parse(ua);
    if (agent == null) {
      return;
    }
    if (agent.getBrowser() != null) {
      row.set("browser", agent.getBrowser().getName());
    }
    row.set("browser_version", agent.getVersion());
    if (agent.getOs() != null) {
      row.set("operating_system", agent.getOs().getName());
    }
  }
}
