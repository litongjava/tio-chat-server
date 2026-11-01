package nexus.io.tio.chat.consts;

/**
 * 推送消息类型，与客户端约定一致
 */
public interface WsRespType {
  String NO_INTERNET = "noInternet";
  String LOGIN_QR_CODE = "loginQrCode";
  String WAITING_AUTHORIZE = "waitingAuthorize";
  String LOGIN_SUCCESS = "loginSuccess";
  String RECEIVE_MESSAGE = "receiveMessage";
  String ONLINE = "online";
  String OFFLINE = "offline";
  String USER_STATE_CHANGE = "userStateChange";
  String ROOM_INFO_CHANGE = "roomInfoChange";
  String MY_ROOM_INFO_CHANGE = "myRoomInfoChange";
  String TOKEN_EXPIRED = "tokenExpired";
  String INVALID_USER = "invalidUser";
  String MSG_MARK_ITEM = "msgMarkItem";
  String MSG_RECALL = "msgRecall";
  String DELETE_FRIEND = "deleteFriend";
  String NOTIFY_EVENT = "notifyEvent";
  String REQUEST_APPROVAL_FRIEND = "requestApprovalFriend";
  String NEW_APPLY = "newApply";
  String ROOM_DISSOLUTION = "roomDissolution";
  String GROUP_SET_ADMIN = "groupSetAdmin";
  String ROOM_GROUP_NOTICE_READ_MSG = "roomGroupNoticeReadMsg";
  String FEED_SEND_MSG = "feedSendMsg";
  String FEED_NOTIFY = "feedNotify";
  String ROOM_NOTIFICATION = "roomNotification";
  String SHIELD = "shield";
  String UNBLOCK = "unblock";
  String MEMBER_CHANGE = "memberChange";
  String WS_RECONNECT = "WSReconnect";
  String JOIN_VIDEO = "JoinVideo";
  String VIDEO_CALL_REQUEST = "VideoCallRequest";
  String START_SIGNALING = "StartSignaling";
  String CALL_ACCEPTED = "CallAccepted";
  String CALL_REJECTED = "CallRejected";
  String ROOM_CLOSED = "RoomClosed";
  String MEDIA_CONTROL = "MediaControl";
  String TIMEOUT = "TIMEOUT";
  String CANCEL = "CANCEL";
  String DROPPED = "DROPPED";
  String WEBRTC_SIGNAL = "WEBRTC_SIGNAL";
  String LEAVE_VIDEO = "LeaveVideo";
  String SCREEN_SHARING_STARTED = "ScreenSharingStarted";
  String SCREEN_SHARING_STOPPED = "ScreenSharingStopped";
  String NETWORK_POOR = "NetworkPoor";
  String USER_KICKED = "UserKicked";
  String ALL_MUTED = "AllMuted";
}
