package nexus.io.tio.chat.ws;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * 按 type 分发上行消息：2 心跳，4~14 音视频信令，15 ACK，16 已读
 */
@Slf4j
public class WsMessageDispatcher {
  public static final int HEARTBEAT = 2;
  private static final Map<Integer, WsMessageProcessor> PROCESSORS = new ConcurrentHashMap<>();

  public static void register(int type, WsMessageProcessor processor) {
    PROCESSORS.put(type, processor);
  }

  @SuppressWarnings("unchecked")
  public void dispatch(ChannelContext channelContext, LoginSession session, String text) {
    Kv frame;
    try {
      frame = JsonUtils.parse(text, Kv.class);
    } catch (Exception e) {
      log.warn("invalid ws frame: {}", text);
      return;
    }
    if (frame == null || frame.get("type") == null) {
      return;
    }
    int type;
    try {
      type = Integer.parseInt(frame.get("type").toString());
    } catch (NumberFormatException e) {
      return;
    }
    if (type == HEARTBEAT) {
      return;
    }
    WsMessageProcessor processor = PROCESSORS.get(type);
    if (processor == null) {
      log.debug("no processor for ws type {}", type);
      return;
    }
    Object raw = frame.get("data");
    Kv data = Kv.create();
    if (raw instanceof Map) {
      data.set((Map<Object, Object>) raw);
    } else if (raw instanceof String && !((String) raw).isBlank()) {
      Kv parsed = JsonUtils.parse((String) raw, Kv.class);
      if (parsed != null) {
        data = parsed;
      }
    }
    processor.process(channelContext, session, data);
  }
}
