package nexus.io.tio.chat.model.generator;

import lombok.Data;

/** 生成项目的请求和默认值 */
@Data
public class ProjectGenerateReq {
  private String author;
  /** CLOUD 注册到 Nacos，BOOT 单独运行 */
  private String type;
  private String outputDir;
  private String projectPrefix;
  private String serviceName;
  private String moduleName;
  private String parent;
  private String groupId;
  private String utilParent;
  private String utilGroupId;
  private String version;
  private String description;
  private Integer serverPort;
  private Boolean seata;
  private String applicationName;
}
