package nexus.io.tio.chat.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import nexus.io.db.activerecord.ActiveRecordPlugin;
import nexus.io.db.activerecord.OrderedFieldContainerFactory;
import nexus.io.db.hikaricp.DsContainer;
import nexus.io.hook.HookCan;
import nexus.io.tio.utils.environment.EnvUtils;

public class DbConfig {

  public void config() {
    String jdbcUrl = EnvUtils.get("jdbc.url");
    if (jdbcUrl == null) {
      return;
    }
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    config.setUsername(EnvUtils.get("jdbc.user"));
    config.setPassword(EnvUtils.get("jdbc.pswd"));
    config.setMaximumPoolSize(EnvUtils.getInt("jdbc.MaximumPoolSize", 10));
    HikariDataSource dataSource = new HikariDataSource(config);
    DsContainer.setDataSource(dataSource);
    HookCan.me().addDestroyMethod(dataSource::close);

    ActiveRecordPlugin arp = new ActiveRecordPlugin(dataSource);
    arp.setContainerFactory(new OrderedFieldContainerFactory());
    arp.setShowSql(EnvUtils.getBoolean("jdbc.showSql", false));
    if (EnvUtils.isDev()) {
      arp.setDevMode(true);
    }
    arp.start();
    HookCan.me().addDestroyMethod(arp::stop);
  }
}
