package nexus.io.tio.chat.ws;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.service.im.chat.ChatService;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.core.ChannelContext;

/**
 * type=15 客户端确认收到消息
 */
public class AckProcessor implements WsMessageProcessor {

  @Override
  public void process(ChannelContext channelContext, LoginSession session, Kv data) {
    Aop.get(ChatService.class).ack(session.getUid(), Values.toLong(data.get("msgId")));
  }
}
