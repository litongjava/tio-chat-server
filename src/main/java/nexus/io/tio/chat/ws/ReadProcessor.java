package nexus.io.tio.chat.ws;

import nexus.io.tio.chat.facade.Facades;
import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.core.ChannelContext;

/**
 * type=16 客户端批量确认消息
 */
public class ReadProcessor implements WsMessageProcessor {

  @Override
  public void process(ChannelContext channelContext, LoginSession session, Kv data) {
    Object ids = data.get("msgId");
    if (!(ids instanceof List)) {
      return;
    }
    for (Long msgId : Values.toLongList((List<?>) ids)) {
      Facades.im().ack(session.getUid(), msgId);
    }
  }
}
