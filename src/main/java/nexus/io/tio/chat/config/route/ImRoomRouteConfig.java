package nexus.io.tio.chat.config.route;

import nexus.io.tio.chat.handler.im.room.AddAdminHandler;
import nexus.io.tio.chat.handler.im.room.AnnouncementDeleteHandler;
import nexus.io.tio.chat.handler.im.room.AnnouncementEditHandler;
import nexus.io.tio.chat.handler.im.room.AnnouncementGetHandler;
import nexus.io.tio.chat.handler.im.room.AnnouncementListHandler;
import nexus.io.tio.chat.handler.im.room.AnnouncementPushHandler;
import nexus.io.tio.chat.handler.im.room.AnnouncementReadHandler;
import nexus.io.tio.chat.handler.im.room.ApplyFriendHandler;
import nexus.io.tio.chat.handler.im.room.ApplyGroupHandler;
import nexus.io.tio.chat.handler.im.room.DeleteApplyHandler;
import nexus.io.tio.chat.handler.im.room.GroupAllHandler;
import nexus.io.tio.chat.handler.im.room.GroupCreateHandler;
import nexus.io.tio.chat.handler.im.room.GroupDetailHandler;
import nexus.io.tio.chat.handler.im.room.GroupDisbandHandler;
import nexus.io.tio.chat.handler.im.room.GroupExitHandler;
import nexus.io.tio.chat.handler.im.room.GroupInfoHandler;
import nexus.io.tio.chat.handler.im.room.GroupInviteHandler;
import nexus.io.tio.chat.handler.im.room.GroupListHandler;
import nexus.io.tio.chat.handler.im.room.GroupListMemberHandler;
import nexus.io.tio.chat.handler.im.room.GroupMemberListHandler;
import nexus.io.tio.chat.handler.im.room.GroupMemberPageHandler;
import nexus.io.tio.chat.handler.im.room.GroupPageHandler;
import nexus.io.tio.chat.handler.im.room.GroupRemoveMemberHandler;
import nexus.io.tio.chat.handler.im.room.GroupSearchHandler;
import nexus.io.tio.chat.handler.im.room.HandleApplyHandler;
import nexus.io.tio.chat.handler.im.room.MemberNicknameHandler;
import nexus.io.tio.chat.handler.im.room.MergeMessageHandler;
import nexus.io.tio.chat.handler.im.room.NoticePageHandler;
import nexus.io.tio.chat.handler.im.room.NoticeReadHandler;
import nexus.io.tio.chat.handler.im.room.NoticeUnreadHandler;
import nexus.io.tio.chat.handler.im.room.RevokeAdminHandler;
import nexus.io.tio.chat.handler.im.room.UpdateMyRoomInfoHandler;
import nexus.io.tio.chat.handler.im.room.UpdateRoomInfoHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class ImRoomRouteConfig {
  private static final String P = "/api/im/room";

  public void config(HttpRequestRouter r) {
    r.post(P + "/apply/apply", new ApplyFriendHandler()::handle);
    r.post(P + "/apply/handler/apply", new HandleApplyHandler()::handle);
    r.post(P + "/apply/group", new ApplyGroupHandler()::handle);
    r.delete(P + "/apply/delete", new DeleteApplyHandler()::handle);
    r.get(P + "/notice/page", new NoticePageHandler()::handle);
    r.post(P + "/notice/read", new NoticeReadHandler()::handle);
    r.get(P + "/notice/unread", new NoticeUnreadHandler()::handle);
    r.get(P + "/group/detail", new GroupDetailHandler()::handle);
    r.get(P + "/group/info", new GroupInfoHandler()::handle);
    r.get(P + "/search", new GroupSearchHandler()::handle);
    r.get(P + "/group/list", new GroupListHandler()::handle);
    r.get(P + "/group/listMember", new GroupListMemberHandler()::handle);
    r.get(P + "/group/member/list", new GroupMemberListHandler()::handle);
    r.get(P + "/group/member/page", new GroupMemberPageHandler()::handle);
    r.delete(P + "/group/member", new GroupRemoveMemberHandler()::handle);
    r.delete(P + "/group/member/exit", new GroupExitHandler()::handle);
    r.post(P + "/group", new GroupCreateHandler()::handle);
    r.post(P + "/group/member", new GroupInviteHandler()::handle);
    r.post(P + "/updateRoomInfo", new UpdateRoomInfoHandler()::handle);
    r.post(P + "/updateMyRoomInfo", new UpdateMyRoomInfoHandler()::handle);
    r.put(P + "/group/admin", new AddAdminHandler()::handle);
    r.delete(P + "/group/admin", new RevokeAdminHandler()::handle);
    r.post(P + "/group/member/nickname", new MemberNicknameHandler()::handle);
    r.post(P + "/group/disband", new GroupDisbandHandler()::handle);
    r.get(P + "/group/all", new GroupAllHandler()::handle);
    r.get(P + "/group/page", new GroupPageHandler()::handle);
    r.get(P + "/announcement/list", new AnnouncementListHandler()::handle);
    r.get(P + "/announcement", new AnnouncementGetHandler()::handle);
    r.post(P + "/announcement/push", new AnnouncementPushHandler()::handle);
    r.post(P + "/announcement/edit", new AnnouncementEditHandler()::handle);
    r.post(P + "/announcement/delete", new AnnouncementDeleteHandler()::handle);
    r.post(P + "/announcement/read", new AnnouncementReadHandler()::handle);
    r.post(P + "/mergeMessage", new MergeMessageHandler()::handle);
  }
}
