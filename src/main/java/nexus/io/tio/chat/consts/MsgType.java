package nexus.io.tio.chat.consts;

/**
 * 消息类型
 */
public interface MsgType {
  int TEXT = 1;
  int RECALL = 2;
  int IMG = 3;
  int FILE = 4;
  int SOUND = 5;
  int VIDEO = 6;
  int EMOJI = 7;
  int SYSTEM = 8;
  int MERGE = 9;
  int NOTICE = 10;
  int BOT = 11;
  int VIDEO_CALL = 12;
  int AUDIO_CALL = 13;
  int MIXED = 14;
  int AIT = 15;
  int REPLY = 16;
  int AI = 17;
  int LOCATION = 18;

  int STATUS_NORMAL = 0;
  int STATUS_DELETE = 1;
}
