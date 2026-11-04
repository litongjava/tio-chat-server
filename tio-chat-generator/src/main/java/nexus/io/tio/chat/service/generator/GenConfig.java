package nexus.io.tio.chat.service.generator;

import nexus.io.tio.utils.environment.EnvUtils;

/**
 * 代码生成的默认配置
 */
final class GenConfig {
  private GenConfig() {
  }

  static String author() {
    return EnvUtils.get("generator.author", "tio-boot");
  }

  static String projectPrefix() {
    return EnvUtils.get("generator.project-prefix", "tio-chat");
  }

  static String parent() {
    return EnvUtils.get("generator.parent", "nexus.io.tio.chat");
  }

  static String outputDir() {
    return EnvUtils.get("generator.output-dir", "");
  }

  static String frontOutputDir() {
    return EnvUtils.get("generator.front-output-dir", "");
  }

  static String frontSoyOutputDir() {
    return EnvUtils.get("generator.front-soy-output-dir", "");
  }

  static String frontVben5OutputDir() {
    return EnvUtils.get("generator.front-vben5-output-dir", "");
  }

  static String tablePrefix() {
    return EnvUtils.get("generator.table-prefix", "");
  }
}
