package nexus.io.tio.chat.service.base;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.model.common.IPageResp;
import nexus.io.tio.chat.service.oauth.TokenService;

/**
 * 在线账号管理：会话列表、终端列表、强制下线
 */
public class OnlineUserService {

  public IPageResp<Kv> page(String username, String nickName, long size, long current) {
    TokenService tokenService = Aop.get(TokenService.class);
    List<Kv> list = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now();
    for (Long userId : tokenService.onlineUserIds()) {
      Kv user = Db.findFirstMap("select username, nick_name from def_user where id=?", userId);
      if (user != null) {
        if (username != null && !username.isEmpty() && !contains(user.getStr("username"), username)) {
          continue;
        }
        if (nickName != null && !nickName.isEmpty() && !contains(user.getStr("nick_name"), nickName)) {
          continue;
        }
      }
      List<LoginSession> sessions = tokenService.sessions(userId);
      if (sessions.isEmpty()) {
        continue;
      }
      LoginSession first = sessions.stream().min(Comparator.comparing(s -> s.getLoginTime() == null ? 0L : s.getLoginTime())).get();
      LocalDateTime sessionTime = time(first.getLoginTime());
      LocalDateTime expireTime = now.plusSeconds(tokenService.ttl(first.getToken()));
      list.add(Kv.by("id", String.valueOf(userId)).set("loginId", String.valueOf(userId))
          .set("username", user == null ? null : user.getStr("username"))
          .set("nickName", user == null ? null : user.getStr("nick_name")).set("sessionTime", sessionTime)
          .set("sessionStr", show(Duration.between(sessionTime, now))).set("expireTime", expireTime)
          .set("expireStr", show(Duration.between(now, expireTime))).set("tokenCount", sessions.size()));
    }
    list.sort((a, b) -> ((LocalDateTime) b.get("sessionTime")).compareTo((LocalDateTime) a.get("sessionTime")));
    int from = (int) Math.min((current - 1) * size, list.size());
    int to = (int) Math.min(current * size, list.size());
    return IPageResp.of(new ArrayList<>(list.subList(from, to)), list.size(), size, current);
  }

  public IPageResp<Kv> tokens(String sessionId, long size, long current) {
    TokenService tokenService = Aop.get(TokenService.class);
    List<Kv> list = new ArrayList<>();
    if (sessionId == null) {
      return IPageResp.of(list, 0, size, current);
    }
    LocalDateTime now = LocalDateTime.now();
    for (LoginSession session : tokenService.sessions(Long.parseLong(sessionId))) {
      LocalDateTime sessionTime = time(session.getLoginTime());
      LocalDateTime expireTime = now.plusSeconds(tokenService.ttl(session.getToken()));
      list.add(Kv.by("tokenValue", session.getToken()).set("deviceType", session.getSystemType() + session.getDeviceType())
          .set("clientId", session.getClientId()).set("sessionTime", sessionTime)
          .set("sessionStr", show(Duration.between(sessionTime, now))).set("expireTime", expireTime)
          .set("expireStr", show(Duration.between(now, expireTime))));
    }
    return IPageResp.of(list, list.size(), size, current);
  }

  public boolean logout(Long userId, String token) {
    TokenService tokenService = Aop.get(TokenService.class);
    if (userId != null) {
      tokenService.kickoutAll(userId);
    }
    if (token != null && !token.isEmpty()) {
      tokenService.kickoutToken(token);
    }
    return true;
  }

  private boolean contains(String value, String keyword) {
    return value != null && value.toLowerCase().contains(keyword.toLowerCase());
  }

  private LocalDateTime time(Long millis) {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis == null ? 0 : millis), ZoneId.systemDefault());
  }

  private String show(Duration duration) {
    long seconds = Math.abs(duration.getSeconds());
    long days = seconds / 86400;
    long hours = seconds % 86400 / 3600;
    long minutes = seconds % 3600 / 60;
    if (days > 0) {
      return days + "天" + hours + "小时";
    }
    if (hours > 0) {
      return hours + "小时" + minutes + "分钟";
    }
    return minutes + "分钟";
  }
}
