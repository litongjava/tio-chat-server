package nexus.io.tio.chat.model.common;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 与 MyBatis-Plus IPage 一致的分页结构：records、total、size、current、pages
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IPageResp<T> {
  private List<T> records;
  private Long total;
  private Long size;
  private Long current;
  private Long pages;

  public static <T> IPageResp<T> of(List<T> records, long total, long size, long current) {
    long pages = size <= 0 ? 0 : (total + size - 1) / size;
    return new IPageResp<>(records == null ? new ArrayList<>() : records, total, size, current, pages);
  }
}
