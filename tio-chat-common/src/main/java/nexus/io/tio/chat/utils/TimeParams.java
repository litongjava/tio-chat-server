package nexus.io.tio.chat.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 解析请求中的时间参数：支持毫秒时间戳、yyyy-MM-dd HH:mm:ss 与 ISO 格式
 */
public class TimeParams {
  private static final DateTimeFormatter DEFAULT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public static LocalDateTime parse(String value, LocalDateTime defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    String text = value.trim();
    try {
      if (text.matches("\\d+")) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(text)), ZoneId.systemDefault());
      }
      if (text.contains("T")) {
        return LocalDateTime.parse(text);
      }
      // java.sql.Timestamp.toString() ends with fractional seconds
      int fraction = text.indexOf('.');
      return LocalDateTime.parse(fraction < 0 ? text : text.substring(0, fraction), DEFAULT);
    } catch (Exception e) {
      return defaultValue;
    }
  }
}
