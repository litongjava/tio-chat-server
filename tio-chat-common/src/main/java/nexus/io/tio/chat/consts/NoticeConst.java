package nexus.io.tio.chat.consts;

/**
 * 通知与申请相关的枚举值
 */
public interface NoticeConst {
  int EVENT_FRIEND_APPLY = 1;
  int EVENT_GROUP_APPLY = 2;
  int EVENT_GROUP_INVITE = 3;
  int EVENT_GROUP_MEMBER_DELETE = 5;
  int EVENT_ADD_ME = 6;
  int EVENT_GROUP_INVITE_ME = 7;
  int EVENT_GROUP_SET_ADMIN = 8;
  int EVENT_GROUP_RECALL_ADMIN = 9;

  int STATUS_UNTREATED = 0;
  int STATUS_ACCEPTED = 1;
  int STATUS_REJECTED = 2;
  int STATUS_IGNORE = 3;

  int UNREAD = 1;
  int READ = 2;

  int APPLY_NORMAL = 0;
  int APPLY_DELETED = 1;
  int TARGET_DELETED = 2;
  int ALL_DELETED = 3;
}
