package nexus.io.tio.chat.config.route;

import nexus.io.tio.chat.handler.im.user.AddBlackHandler;
import nexus.io.tio.chat.handler.im.user.BadgesHandler;
import nexus.io.tio.chat.handler.im.user.BindEmailHandler;
import nexus.io.tio.chat.handler.im.user.BlackPageHandler;
import nexus.io.tio.chat.handler.im.user.CheckImEmailHandler;
import nexus.io.tio.chat.handler.im.user.EditBlackHandler;
import nexus.io.tio.chat.handler.im.user.FindUidByUserIdHandler;
import nexus.io.tio.chat.handler.im.user.GetUserByIdHandler;
import nexus.io.tio.chat.handler.im.user.GetUserByIdsHandler;
import nexus.io.tio.chat.handler.im.user.IsBlackHandler;
import nexus.io.tio.chat.handler.im.user.ItemInfoBatchHandler;
import nexus.io.tio.chat.handler.im.user.ModifyAvatarHandler;
import nexus.io.tio.chat.handler.im.user.ModifyInfoHandler;
import nexus.io.tio.chat.handler.im.user.RefreshIpInfoHandler;
import nexus.io.tio.chat.handler.im.user.RemoveBlackHandler;
import nexus.io.tio.chat.handler.im.user.SearchUserHandler;
import nexus.io.tio.chat.handler.im.user.UserInfoHandler;
import nexus.io.tio.chat.handler.im.user.WearingBadgeHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class ImUserRouteConfig {
  private static final String P = "/api/im/user";

  public void config(HttpRequestRouter r) {
    r.get(P + "/userInfo", new UserInfoHandler()::handle);
    r.post(P + "/getUserByIds", new GetUserByIdsHandler()::handle);
    r.get(P + "/getById/{id}", new GetUserByIdHandler()::handle);
    r.get(P + "/findById", new FindUidByUserIdHandler()::handle);
    r.get(P + "/checkEmail", new CheckImEmailHandler()::handle);
    r.post(P + "/refreshIpInfo", new RefreshIpInfoHandler()::handle);
    r.post(P + "/avatar", new ModifyAvatarHandler()::handle);
    r.post(P + "/bindEmail", new BindEmailHandler()::handle);
    r.post(P + "/badges/batch", new ItemInfoBatchHandler()::handle);
    r.put(P + "/info", new ModifyInfoHandler()::handle);
    r.get(P + "/badges", new BadgesHandler()::handle);
    r.put(P + "/badge", new WearingBadgeHandler()::handle);
    r.get(P + "/search", new SearchUserHandler()::handle);
    r.get(P + "/black/page", new BlackPageHandler()::handle);
    r.put(P + "/black", new AddBlackHandler()::handle);
    r.post(P + "/black/edit", new EditBlackHandler()::handle);
    r.post(P + "/black/remove", new RemoveBlackHandler()::handle);
    r.get(P + "/black/isBlack", new IsBlackHandler()::handle);
  }
}
