package nexus.io.tio.chat.service.generator;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.utils.ChatErrors;

/**
 * 读取数据源的表和字段。dsId 为空时读取当前服务的数据库，否则读取 def_datasource_config 中的配置。
 * MySQL 使用 information_schema，其他数据库使用 JDBC 元数据
 */
class GenDbMeta {
  private static final int LOGIN_TIMEOUT_SECONDS = 5;
  private static final Pattern URL = Pattern.compile("^jdbc:(mysql|mariadb|postgresql|oracle|sqlserver):.*");
  /** 这些连接参数可以让驱动读取本地文件、反序列化或加载任意类 */
  private static final Pattern UNSAFE_URL = Pattern.compile(
      "(?i).*(allowLoadLocalInfile|autoDeserialize|allowUrlInLocalInfile|queryInterceptors|socketFactory|sslfactory|loggerFile|authenticationPluginClassName).*");

  List<Kv> tables(Long dsId) {
    try (Connection connection = open(dsId)) {
      return isMysql(connection) ? mysqlTables(connection) : jdbcTables(connection);
    } catch (SQLException e) {
      throw ChatErrors.of("读取数据源失败：" + e.getMessage());
    }
  }

  private List<Kv> mysqlTables(Connection connection) throws SQLException {
    String sql = """
        select table_name, table_comment from information_schema.tables
        where table_schema=database() and table_type='BASE TABLE' order by table_name
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rs = statement.executeQuery()) {
      List<Kv> result = new ArrayList<>();
      while (rs.next()) {
        result.add(Kv.by("name", rs.getString(1)).set("comment", rs.getString(2)));
      }
      return result;
    }
  }

  private List<Kv> jdbcTables(Connection connection) throws SQLException {
    List<Kv> result = new ArrayList<>();
    try (ResultSet rs = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), "%", new String[] { "TABLE" })) {
      while (rs.next()) {
        result.add(Kv.by("name", rs.getString("TABLE_NAME")).set("comment", rs.getString("REMARKS")));
      }
    }
    return result;
  }

  Kv table(Long dsId, String name) {
    try (Connection connection = open(dsId)) {
      if (!isMysql(connection)) {
        return jdbcTable(connection, name);
      }
      String sql = """
          select table_comment from information_schema.tables where table_schema=database() and table_name=?
          """;
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, name);
        try (ResultSet rs = statement.executeQuery()) {
          ChatErrors.require(rs.next(), "表不存在：" + name);
          return Kv.by("name", name).set("comment", rs.getString(1)).set("columns", mysqlColumns(connection, name));
        }
      }
    } catch (SQLException e) {
      throw ChatErrors.of("读取数据源失败：" + e.getMessage());
    }
  }

  private Kv jdbcTable(Connection connection, String name) throws SQLException {
    for (Kv table : jdbcTables(connection)) {
      if (name.equals(table.getStr("name"))) {
        return table.set("columns", jdbcColumns(connection, name));
      }
    }
    throw ChatErrors.of("表不存在：" + name);
  }

  @SuppressWarnings("unchecked")
  List<Kv> columns(Long dsId, String tableName) {
    return (List<Kv>) table(dsId, tableName).get("columns");
  }

  private List<Kv> mysqlColumns(Connection connection, String tableName) throws SQLException {
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

  private List<Kv> jdbcColumns(Connection connection, String tableName) throws SQLException {
    DatabaseMetaData metaData = connection.getMetaData();
    String catalog = connection.getCatalog();
    String schema = connection.getSchema();
    Set<String> primaryKeys = new HashSet<>();
    try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
      while (rs.next()) {
        primaryKeys.add(rs.getString("COLUMN_NAME"));
      }
    }
    List<Kv> result = new ArrayList<>();
    try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, "%")) {
      while (rs.next()) {
        String name = rs.getString("COLUMN_NAME");
        String type = jdbcType(rs.getString("TYPE_NAME"), rs.getInt("COLUMN_SIZE"), rs.getInt("DECIMAL_DIGITS"));
        result.add(Kv.by("name", name).set("columnType", type).set("dataType", rs.getString("TYPE_NAME")).set("comment", rs.getString("REMARKS"))
            .set("nullable", "YES".equals(rs.getString("IS_NULLABLE"))).set("pk", primaryKeys.contains(name))
            .set("autoIncrement", "YES".equals(rs.getString("IS_AUTOINCREMENT"))).set("columnDef", rs.getString("COLUMN_DEF"))
            .set("size", rs.getInt("COLUMN_SIZE")));
      }
    }
    return result;
  }

  /** Oracle 的 NUMBER 由精度决定类型 */
  private static String jdbcType(String typeName, int size, int digits) {
    if (!"number".equalsIgnoreCase(typeName)) {
      return typeName;
    }
    if (digits > 0) {
      return "decimal";
    }
    return size > 9 ? "bigint" : "int";
  }

  /** 把其他数据库的类型名换成 GenColumns 使用的类型名 */
  static String normalizeType(String columnType) {
    String type = columnType.toLowerCase(Locale.ROOT);
    return switch (type.replaceAll("\\(.*\\)", "").trim()) {
    case "int8", "bigserial", "serial8" -> "bigint";
    case "int4", "int2", "serial", "serial4", "smallserial", "serial2" -> "int";
    case "bool", "boolean" -> "tinyint(1)";
    case "numeric", "money", "smallmoney" -> "decimal";
    case "float8", "double precision", "binary_double" -> "double";
    case "float4", "real", "binary_float" -> "float";
    case "timestamptz", "timestamp with time zone", "datetime2", "smalldatetime", "datetimeoffset" -> "datetime";
    case "timetz" -> "time";
    case "bytea", "image", "varbinary", "raw" -> "blob";
    case "clob", "nclob", "ntext", "uuid", "uniqueidentifier", "xml" -> "text";
    default -> type;
    };
  }

  /** 数据库类型，模板按它生成 SQL */
  static String dbType(Long dsId) {
    if (dsId == null || dsId == 0) {
      return "mysql";
    }
    Kv config = Db.findFirstMap("select url from def_datasource_config where id=? and is_del=0", dsId);
    String url = config == null ? null : config.getStr("url");
    if (url == null || !url.startsWith("jdbc:")) {
      return "mysql";
    }
    String name = url.substring("jdbc:".length());
    return name.substring(0, name.indexOf(':') < 0 ? name.length() : name.indexOf(':'));
  }

  private boolean isMysql(Connection connection) throws SQLException {
    String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    return product.contains("mysql") || product.contains("mariadb");
  }

  private Connection open(Long dsId) throws SQLException {
    if (dsId == null || dsId == 0) {
      return Db.use().getConfig().getConnection();
    }
    Kv config = Db.findFirstMap("select * from def_datasource_config where id=? and is_del=0", dsId);
    ChatErrors.require(config != null, "数据源不存在");
    String url = config.getStr("url");
    ChatErrors.require(url != null && URL.matcher(url).matches(), "代码生成只支持 MySQL、MariaDB、PostgreSQL、Oracle 和 SQL Server 数据源");
    ChatErrors.require(!UNSAFE_URL.matcher(url).matches(), "数据源连接串包含不允许的参数");
    DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
    try {
      return DriverManager.getConnection(url, config.getStr("username"), config.getStr("password"));
    } catch (SQLException e) {
      if (e.getMessage() != null && e.getMessage().contains("No suitable driver")) {
        throw ChatErrors.of("服务没有包含这个数据库的驱动，请把驱动加入 classpath：" + config.getStr("driver_class_name"));
      }
      throw e;
    }
  }
}
