package nexus.io.tio.chat.service.im.chat.msg;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import nexus.io.tio.chat.utils.ChatErrors;

/**
 * 消息类型与处理器的映射
 */
public class MsgHandlers {
  private static final Map<Integer, MsgHandler> HANDLERS = new ConcurrentHashMap<>();

  static {
    register(new TextMsgHandler());
    register(new BotMsgHandler());
    register(new RecallMsgHandler());
    register(new ImgMsgHandler());
    register(new FileMsgHandler());
    register(new SoundMsgHandler());
    register(new VideoMsgHandler());
    register(new EmojisMsgHandler());
    register(new SystemMsgHandler());
    register(new MergeMsgHandler());
    register(new NoticeMsgHandler());
    register(new CallMsgHandler(true));
    register(new CallMsgHandler(false));
    register(new LocationMsgHandler());
  }

  public static void register(MsgHandler handler) {
    HANDLERS.put(handler.type(), handler);
  }

  public static MsgHandler get(Integer type) {
    MsgHandler handler = type == null ? null : HANDLERS.get(type);
    if (handler == null) {
      throw ChatErrors.of("参数校验失败");
    }
    return handler;
  }

  public static MsgHandler find(Integer type) {
    return type == null ? null : HANDLERS.get(type);
  }
}
