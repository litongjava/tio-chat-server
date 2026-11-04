package nexus.io.tio.chat.model.generator;

import java.util.List;

import lombok.Data;

/** 导入表的请求 */
@Data
public class ImportTableReq {
  private Long dsId;
  private List<String> tableNames;
}
