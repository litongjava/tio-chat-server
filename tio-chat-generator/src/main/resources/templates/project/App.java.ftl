package ${basePackage};

import nexus.io.tio.boot.TioApplication;
import ${basePackage}.config.${appName}Config;

/**
 * ${description}
 *
 * @author ${author}
 */
public class ${appName}App {
  public static void main(String[] args) {
    TioApplication.run(${appName}App.class, new ${appName}Config(), args);
  }
}
