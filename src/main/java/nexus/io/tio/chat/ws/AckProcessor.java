package nexus.io.tio.chat.ws;

import nexus.io.tio.chat.facade.Facades;
import com.jfinal.kit.Kv;

import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.core.ChannelContext;

/**
 * type=15 客户端确认收到消息
 */
public class AckProcessor implements WsMessageProcessor {

  @Override
  public void process(ChannelContext channelContext, LoginSession session, Kv data) {
    Facades.im().ack(session.getUid(), Values.toLong(data.get("msgId")));
  }
}
