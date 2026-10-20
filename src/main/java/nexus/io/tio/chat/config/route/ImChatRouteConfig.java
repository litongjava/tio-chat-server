package nexus.io.tio.chat.config.route;

import nexus.io.tio.chat.handler.im.chat.MsgListHandler;
import nexus.io.tio.chat.handler.im.chat.MsgMarkHandler;
import nexus.io.tio.chat.handler.im.chat.MsgPageHandler;
import nexus.io.tio.chat.handler.im.chat.MsgReadHandler;
import nexus.io.tio.chat.handler.im.chat.MsgReadInfoHandler;
import nexus.io.tio.chat.handler.im.chat.MsgReadPageHandler;
import nexus.io.tio.chat.handler.im.chat.MsgRecallHandler;
import nexus.io.tio.chat.handler.im.chat.SendMsgHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class ImChatRouteConfig {
  private static final String P = "/api/im/chat";

  public void config(HttpRequestRouter r) {
    r.get(P + "/msg/page", new MsgPageHandler()::handle);
    r.post(P + "/msg/list", new MsgListHandler()::handle);
    r.post(P + "/msg", new SendMsgHandler()::handle);
    r.put(P + "/msg/mark", new MsgMarkHandler()::handle);
    r.put(P + "/msg/recall", new MsgRecallHandler()::handle);
    r.get(P + "/msg/read/page", new MsgReadPageHandler()::handle);
    r.get(P + "/msg/read", new MsgReadInfoHandler()::handle);
    r.put(P + "/msg/read", new MsgReadHandler()::handle);
  }
}
