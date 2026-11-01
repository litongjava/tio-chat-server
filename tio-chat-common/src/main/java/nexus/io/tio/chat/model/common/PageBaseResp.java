package nexus.io.tio.chat.model.common;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 页码分页响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageBaseResp<T> {
  private Integer pageNo;
  private Integer pageSize;
  private Long totalRecords;
  private Boolean isLast = Boolean.FALSE;
  private List<T> list;

  public static <T> PageBaseResp<T> init(int pageNo, int pageSize, long total, List<T> list) {
    boolean isLast = (long) pageNo * pageSize >= total;
    return new PageBaseResp<>(pageNo, pageSize, total, isLast, list == null ? new ArrayList<>() : list);
  }

  public static <T> PageBaseResp<T> empty() {
    return new PageBaseResp<>(0, 0, 0L, true, new ArrayList<>());
  }
}
