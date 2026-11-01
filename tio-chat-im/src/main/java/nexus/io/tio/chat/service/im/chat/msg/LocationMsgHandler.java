package nexus.io.tio.chat.service.im.chat.msg;

import java.util.LinkedHashMap;
import java.util.Map;

import nexus.io.tio.chat.consts.MsgType;
import nexus.io.tio.chat.model.im.chat.MessageRecord;

public class LocationMsgHandler extends AbstractMsgHandler {
  private static final String[] FIELDS = { "latitude", "longitude", "address", "precision", "timestamp" };

  @Override
  public int type() {
    return MsgType.LOCATION;
  }

  @Override
  public void fill(MessageRecord message, Map<String, Object> body) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (String field : FIELDS) {
      Object value = body.get(field);
      map.put(field, value == null ? null : value.toString());
    }
    message.setContent(map.get("address") == null ? null : map.get("address").toString());
    message.getExtra().put("mapMsgDTO", map);
  }

  @Override
  public Object show(MessageRecord message, Long receiveUid) {
    return message.extraMap("mapMsgDTO");
  }

  @Override
  public Object showReply(MessageRecord message) {
    return "[位置信息]";
  }

  @Override
  public String showContact(MessageRecord message) {
    return "[位置信息]";
  }
}
