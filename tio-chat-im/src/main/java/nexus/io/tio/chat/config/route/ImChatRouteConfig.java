package nexus.io.tio.chat.config.route;

import nexus.io.tio.chat.handler.im.chat.ContactCreateHandler;
import nexus.io.tio.chat.handler.im.chat.ContactDetailByFriendHandler;
import nexus.io.tio.chat.handler.im.chat.ContactDetailHandler;
import nexus.io.tio.chat.handler.im.chat.ContactListHandler;
import nexus.io.tio.chat.handler.im.chat.ContactNotificationHandler;
import nexus.io.tio.chat.handler.im.chat.ContactPageHandler;
import nexus.io.tio.chat.handler.im.chat.ContactSetHideHandler;
import nexus.io.tio.chat.handler.im.chat.ContactSetShieldHandler;
import nexus.io.tio.chat.handler.im.chat.ContactSetTopHandler;
import nexus.io.tio.chat.handler.im.chat.DeleteSessionHandler;
import nexus.io.tio.chat.handler.im.chat.MsgFilesHandler;
import nexus.io.tio.chat.handler.im.chat.MsgListHandler;
import nexus.io.tio.chat.handler.im.chat.MsgMarkHandler;
import nexus.io.tio.chat.handler.im.chat.MsgPageHandler;
import nexus.io.tio.chat.handler.im.chat.MsgReadHandler;
import nexus.io.tio.chat.handler.im.chat.MsgReadInfoHandler;
import nexus.io.tio.chat.handler.im.chat.MsgReadPageHandler;
import nexus.io.tio.chat.handler.im.chat.MsgRecallHandler;
import nexus.io.tio.chat.handler.im.chat.MsgSearchHandler;
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
    r.get(P + "/msg/search", new MsgSearchHandler()::handle);
    r.get(P + "/msg/files", new MsgFilesHandler()::handle);
    r.put(P + "/msg/read", new MsgReadHandler()::handle);
    r.post(P + "/contact/create", new ContactCreateHandler()::handle);
    r.get(P + "/contact/page", new ContactPageHandler()::handle);
    r.get(P + "/contact/list", new ContactListHandler()::handle);
    r.get(P + "/contact/detail", new ContactDetailHandler()::handle);
    r.get(P + "/contact/detail/friend", new ContactDetailByFriendHandler()::handle);
    r.post(P + "/setTop", new ContactSetTopHandler()::handle);
    r.post(P + "/setHide", new ContactSetHideHandler()::handle);
    r.post(P + "/notification", new ContactNotificationHandler()::handle);
    r.post(P + "/setShield", new ContactSetShieldHandler()::handle);
    r.delete(P + "/delete", new DeleteSessionHandler()::handle);
  }
}
