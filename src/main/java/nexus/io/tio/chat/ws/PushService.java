package nexus.io.tio.chat.ws;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import nexus.io.redis.Redis;
import nexus.io.tio.boot.server.TioBootServer;
import nexus.io.tio.chat.model.ws.WsBaseResp;
import nexus.io.tio.core.Tio;
import nexus.io.tio.server.ServerTioConfig;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.websocket.common.WebSocketResponse;
import redis.clients.jedis.JedisPubSub;

/**
 * WebSocket 推送：先投递本节点连接，再通过 Redis 频道转发给其他节点
 */
@Slf4j
public class PushService {
  public static final String CHANNEL = "chat:ws:push";
  public static final String NODE_ID = "ws" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

  /**
   * 推送给多个用户的全部设备
   */
  public void push(WsBaseResp msg, Collection<Long> uids) {
    if (uids == null || uids.isEmpty()) {
      return;
    }
    List<Long> list = new ArrayList<>(uids);
    String text = JsonUtils.toJson(msg);
    deliverLocal(text, list, null);
    publish(Kv.by("node", NODE_ID).set("text", text).set("uids", list));
  }

  public void push(WsBaseResp msg, Long uid) {
    if (uid != null) {
      push(msg, List.of(uid));
    }
  }

  /**
   * 推送给某个用户的某台设备
   */
  public void pushToDevice(WsBaseResp msg, long uid, String clientId) {
    String text = JsonUtils.toJson(msg);
    deliverLocal(text, null, WsSessions.deviceGroup(uid, clientId));
    publish(Kv.by("node", NODE_ID).set("text", text).set("group", WsSessions.deviceGroup(uid, clientId)));
  }

  private void publish(Kv payload) {
    try {
      Redis.use().publish(CHANNEL, JsonUtils.toJson(payload));
    } catch (Exception e) {
      log.error("publish ws message failed", e);
    }
  }

  private void deliverLocal(String text, List<Long> uids, String group) {
    ServerTioConfig config = TioBootServer.me().getServerTioConfig();
    if (config == null) {
      return;
    }
    if (group != null) {
      Tio.sendToGroup(config, group, WebSocketResponse.fromText(text, "utf-8"));
      return;
    }
    for (Long uid : uids) {
      if (uid != null) {
        Tio.sendToUser(config, uid.toString(), WebSocketResponse.fromText(text, "utf-8"));
      }
    }
  }

  /**
   * 订阅其他节点转发的推送
   */
  public void subscribe() {
    JedisPubSub pubSub = new JedisPubSub() {
      @Override
      public void onMessage(String channel, String message) {
        try {
          Kv payload = JsonUtils.parse(message, Kv.class);
          if (NODE_ID.equals(payload.getStr("node"))) {
            return;
          }
          Object uids = payload.get("uids");
          List<Long> list = new ArrayList<>();
          if (uids instanceof Collection) {
            for (Object uid : (Collection<?>) uids) {
              list.add(Long.parseLong(uid.toString()));
            }
          }
          deliverLocal(payload.getStr("text"), list, payload.getStr("group"));
        } catch (Exception e) {
          log.error("handle ws relay failed: {}", message, e);
        }
      }
    };
    Redis.use().subscribeThread(pubSub, CHANNEL);
  }
}
