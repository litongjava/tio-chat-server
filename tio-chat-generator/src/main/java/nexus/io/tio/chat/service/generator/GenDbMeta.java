package nexus.io.tio.chat.service.generator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.utils.ChatErrors;

/**
 * 读取 MySQL 数据源的表和字段。dsId 为空时读取当前服务的数据库，否则读取 def_datasource_config 中的配置
 */
class GenDbMeta {
  private static final int LOGIN_TIMEOUT_SECONDS = 5;

  List<Kv> tables(Long dsId) {
    String sql = """
        select table_name, table_comment from information_schema.tables
        where table_schema=database() and table_type='BASE TABLE' order by table_name
        """;
    try (Connection connection = open(dsId); PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery()) {
      List<Kv> result = new ArrayList<>();
      while (rs.next()) {
        result.add(Kv.by("name", rs.getString(1)).set("comment", rs.getString(2)));
      }
      return result;
    } catch (SQLException e) {
      throw ChatErrors.of("读取数据源失败：" + e.getMessage());
    }
  }

  Kv table(Long dsId, String name) {
    String sql = """
        select table_comment from information_schema.tables where table_schema=database() and table_name=?
        """;
    try (Connection connection = open(dsId); PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      try (ResultSet rs = statement.executeQuery()) {
        ChatErrors.require(rs.next(), "表不存在：" + name);
        return Kv.by("name", name).set("comment", rs.getString(1)).set("columns", columns(connection, name));
      }
    } catch (SQLException e) {
      throw ChatErrors.of("读取数据源失败：" + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  List<Kv> columns(Long dsId, String tableName) {
    return (List<Kv>) table(dsId, tableName).get("columns");
  }

  private List<Kv> columns(Connection connection, String tableName) throws SQLException {
    String sql = """
        select column_name, column_type, data_type, column_comment, is_nullable, column_key, extra, column_default,
          coalesce(character_maximum_length, numeric_precision, datetime_precision) size_
        from information_schema.columns
        where table_schema=database() and table_name=? order by ordinal_position
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tableName);
      try (ResultSet rs = statement.executeQuery()) {
        List<Kv> result = new ArrayList<>();
        while (rs.next()) {
          result.add(Kv.by("name", rs.getString(1)).set("columnType", rs.getString(2)).set("dataType", rs.getString(3))
              .set("comment", rs.getString(4)).set("nullable", "YES".equals(rs.getString(5)))
              .set("pk", "PRI".equals(rs.getString(6)))
              .set("autoIncrement", rs.getString(7) != null && rs.getString(7).contains("auto_increment"))
              .set("columnDef", rs.getString(8)).set("size", rs.getObject(9)));
        }
        return result;
      }
    }
  }

  private Connection open(Long dsId) throws SQLException {
    if (dsId == null || dsId == 0) {
      return Db.use().getConfig().getConnection();
    }
    Kv config = Db.findFirstMap("select * from def_datasource_config where id=? and is_del=0", dsId);
    ChatErrors.require(config != null, "数据源不存在");
    String url = config.getStr("url");
    // 只读取 MySQL 的 information_schema，其他驱动的连接串不会被打开
    ChatErrors.require(url != null && url.startsWith("jdbc:mysql:"), "代码生成只支持 MySQL 数据源");
    DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
    return DriverManager.getConnection(url, config.getStr("username"), config.getStr("password"));
  }
}
