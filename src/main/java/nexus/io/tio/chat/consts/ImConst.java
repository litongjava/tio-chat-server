package nexus.io.tio.chat.consts;

/**
 * IM 默认值与枚举值，与原数据库中的约定一致
 */
public interface ImConst {
  long DEF_BOT_ID = 1L;
  long DEF_ROOM_ID = 1L;
  long DEF_GROUP_ID = 1L;

  int ROOM_TYPE_GROUP = 1;
  int ROOM_TYPE_FRIEND = 2;

  int GROUP_ROLE_LEADER = 1;
  int GROUP_ROLE_MANAGER = 2;
  int GROUP_ROLE_MEMBER = 3;

  int USER_TYPE_BOT = 2;
  int USER_TYPE_NORMAL = 3;

  int SYSTEM_TYPE_MANAGER = 1;
  int SYSTEM_TYPE_IM = 2;
}
