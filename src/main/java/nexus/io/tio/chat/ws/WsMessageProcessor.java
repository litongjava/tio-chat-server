package nexus.io.tio.chat.ws;

import com.jfinal.kit.Kv;

import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.core.ChannelContext;

/**
 * 处理客户端上行的一类 WebSocket 消息
 */
public interface WsMessageProcessor {
  void process(ChannelContext channelContext, LoginSession session, Kv data);
}
