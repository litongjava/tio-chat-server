package nexus.io.tio.chat.model.im.chat;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.jfinal.kit.Kv;

import lombok.Data;
import lombok.NoArgsConstructor;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * im_message 记录，extra 列解析为 Map
 */
@Data
@NoArgsConstructor
public class MessageRecord {
  private Long id;
  private Long roomId;
  private Long fromUid;
  private String content;
  private Long replyMsgId;
  private Integer status;
  private Integer gapCount;
  private Integer type;
  private Map<String, Object> extra;
  private LocalDateTime createTime;

  public static MessageRecord from(Kv row) {
    if (row == null) {
      return null;
    }
    MessageRecord m = new MessageRecord();
    m.setId(row.getLong("id"));
    m.setRoomId(row.getLong("room_id"));
    m.setFromUid(row.getLong("from_uid"));
    m.setContent(row.getStr("content"));
    m.setReplyMsgId(Values.toLong(row.get("reply_msg_id")));
    m.setStatus(Values.toInt(row.get("status")));
    m.setGapCount(Values.toInt(row.get("gap_count")));
    m.setType(Values.toInt(row.get("type")));
    m.setCreateTime(Values.time(row.get("create_time")));
    Object extra = row.get("extra");
    Map<String, Object> map = new LinkedHashMap<>();
    if (extra != null && !extra.toString().isBlank()) {
      Map<String, Object> parsed = JsonUtils.parseToMap(extra.toString(), String.class, Object.class);
      if (parsed != null) {
        map.putAll(parsed);
      }
    }
    m.setExtra(map);
    return m;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> extraMap(String key) {
    Object value = extra == null ? null : extra.get(key);
    return value instanceof Map ? (Map<String, Object>) value : null;
  }
}
