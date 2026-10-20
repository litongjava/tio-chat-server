package nexus.io.tio.chat.service.oauth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import nexus.io.tio.chat.context.LoginSession;

/**
 * 登录相关事件，WebSocket 模块注册监听后推送下线通知
 */
public class LoginEventService {
  private static final List<BiConsumer<LoginSession, String>> KICKOUT_LISTENERS = new ArrayList<>();
  private static final List<Consumer<LoginSession>> OFFLINE_LISTENERS = new ArrayList<>();

  public static void addKickoutListener(BiConsumer<LoginSession, String> listener) {
    KICKOUT_LISTENERS.add(listener);
  }

  public static void addOfflineListener(Consumer<LoginSession> listener) {
    OFFLINE_LISTENERS.add(listener);
  }

  /**
   * @param session 新会话
   * @param kickedToken 被挤下线的旧 token
   */
  public void onKickout(LoginSession session, String kickedToken, String ip) {
    for (BiConsumer<LoginSession, String> listener : KICKOUT_LISTENERS) {
      listener.accept(session, kickedToken);
    }
  }

  public void onOffline(LoginSession session) {
    for (Consumer<LoginSession> listener : OFFLINE_LISTENERS) {
      listener.accept(session);
    }
  }
}
