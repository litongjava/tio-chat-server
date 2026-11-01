package nexus.io.tio.chat.model.common;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游标分页响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CursorPageBaseResp<T> {
  private String cursor;
  private Boolean isLast = Boolean.FALSE;
  private List<T> list;
  private Long total;

  public static <T> CursorPageBaseResp<T> empty() {
    return new CursorPageBaseResp<>(null, true, new ArrayList<>(), 0L);
  }
}
