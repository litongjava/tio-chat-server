package nexus.io.tio.chat.utils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;

/**
 * 数据库值转换
 */
public class Values {

  public static LocalDateTime time(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof LocalDateTime) {
      return (LocalDateTime) value;
    }
    if (value instanceof Timestamp) {
      return ((Timestamp) value).toLocalDateTime();
    }
    if (value instanceof Date) {
      return new Timestamp(((Date) value).getTime()).toLocalDateTime();
    }
    return null;
  }

  public static Long toLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    String text = value.toString().trim();
    if (text.isEmpty() || "null".equals(text) || "undefined".equals(text)) {
      return null;
    }
    return Long.parseLong(text);
  }

  public static Integer toInt(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean) {
      return (Boolean) value ? 1 : 0;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    String text = value.toString().trim();
    if (text.isEmpty()) {
      return null;
    }
    return Integer.parseInt(text);
  }

  public static Boolean toBool(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue() != 0;
    }
    return "1".equals(value.toString()) || "true".equalsIgnoreCase(value.toString());
  }

  public static boolean isTrue(Object value) {
    return Boolean.TRUE.equals(toBool(value));
  }

  public static List<Long> toLongList(Collection<?> values) {
    List<Long> result = new ArrayList<>();
    if (values == null) {
      return result;
    }
    for (Object value : values) {
      Long id = toLong(value);
      if (id != null) {
        result.add(id);
      }
    }
    return result;
  }

  /**
   * 生成 in 查询的占位符，例如 ?,?,?
   */
  public static String placeholders(int size) {
    StringJoiner joiner = new StringJoiner(",");
    for (int i = 0; i < size; i++) {
      joiner.add("?");
    }
    return joiner.toString();
  }
}
