package nexus.io.tio.chat.model.generator;

import java.util.List;
import java.util.Map;

import lombok.Data;

/** 生成代码的请求 */
@Data
public class GenerateReq {
  private List<Long> ids;
  /** BACKEND、WEB_PLUS、WEB_SOYBEAN、WEB_VBEN5 */
  private String template;
  /** 模板路径 -> OVERRIDE、ADD、IGNORE、EXIST_IGNORE */
  private Map<String, String> fileOverrideConfig;
}
