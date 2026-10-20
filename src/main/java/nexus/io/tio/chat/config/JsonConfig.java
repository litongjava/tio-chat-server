package nexus.io.tio.chat.config;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import nexus.io.tio.utils.json.TioJson;
import nexus.io.tio.utils.json.TioToJson;

/**
 * 客户端约定：Long 输出为字符串（框架默认行为），时间输出为毫秒时间戳。
 */
public class JsonConfig {

  public void config() {
    TioToJson<Date> dateToJson = (value, depth, ret) -> ret.addNumber(value.getTime());
    TioToJson<Timestamp> timestampToJson = (value, depth, ret) -> ret.addNumber(value.getTime());
    TioToJson<LocalDateTime> localDateTimeToJson = (value, depth, ret) -> {
      ret.addNumber(value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    };
    TioJson.addToJson(Date.class, dateToJson);
    TioJson.addToJson(Timestamp.class, timestampToJson);
    TioJson.addToJson(LocalDateTime.class, localDateTimeToJson);
  }
}
