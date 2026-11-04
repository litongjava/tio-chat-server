package nexus.io.tio.chat.service.im;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.utils.ChatErrors;

/**
 * 数据源配置：测试连接
 */
@Slf4j
public class DatasourceConfigService {
  private static final int LOGIN_TIMEOUT_SECONDS = 3;

  public boolean testConnection(Long id) {
    Kv config = Db.findFirstMap("select * from def_datasource_config where id=? and is_del=0", id);
    if (config == null) {
      throw ChatErrors.of("请先配置数据源:" + id);
    }
    String url = config.getStr("url");
    try {
      String driver = config.getStr("driver_class_name");
      if (driver != null && !driver.isEmpty()) {
        Class.forName(driver);
      }
      DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
      try (Connection connection = DriverManager.getConnection(url, config.getStr("username"), config.getStr("password"))) {
        return connection != null;
      }
    } catch (ClassNotFoundException | SQLException e) {
      log.warn("test datasource {} failed: {}", url, e.getMessage());
      throw ChatErrors.of("创建测试链接错误 " + url);
    }
  }
}
