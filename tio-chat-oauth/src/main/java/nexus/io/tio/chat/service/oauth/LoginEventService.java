package nexus.io.tio.chat.service.oauth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.WsRespType;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.model.ws.OffLineResp;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.chat.ws.PushService;

/**
 * 登录相关事件：同类设备重复登录时推送下线通知，其他模块可注册额外监听
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
    // 通知旧设备下线并断开旧 token 的连接；经 Redis 转发，WebSocket 服务单独部署时同样生效
    OffLineResp resp = new OffLineResp(session.getUid(), session.getDeviceType(), session.getClientId(), null,
        kickedToken);
    PushService pushService = Aop.get(PushService.class);
    pushService.push(WsBaseResp.of(WsRespType.TOKEN_EXPIRED, resp), session.getUid());
    pushService.unbindToken(kickedToken);
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
