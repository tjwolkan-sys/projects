package com.powerdialer.engine.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.powerdialer.model.ParametrizedQuery;
import com.powerdialer.util.Constants.DB_TYPE;

/**
 * Database engine to connect to databases dynamically
 */
@Component
public class DBEngine {

  private static final Logger LOG = LoggerFactory.getLogger(DBEngine.class);
  /**
   * FIX-333607: campaign aksiyonları paralel çalıştığı için eşzamanlı okunup yazılır; null anahtar
   * (geçici datasource) bu map'e hiç yazılmaz, okuma noktaları null'a karşı korunur.
   */
  private Map<Long, BasicDataSource> dataSources = new ConcurrentHashMap<>();
  /** Aynı campaign için datasource'un tek kez oluşturulmasını sağlayan campaign bazlı kilitler. */
  private final Map<Long, Object> dataSourceCreationLocks = new ConcurrentHashMap<>();

  @Autowired
  private ApplicationContext applicationContext;

  /**
   * Reference to DBEngine bean processed by Spring
   */
  private DBEngine self;

  @PostConstruct
  public void init() {
    self = applicationContext.getBean(this.getClass());
  }

  @Value("${db.connectionURL}")
  private String mainDatabaseUrl;
  @Value("${db.username}")
  private String mainDatabaseUsername;
  @Value("${db.password}")
  private String mainDatabasePassword;

  private static final Pattern MSSQL_DBNAME =
          Pattern.compile("(?i)(?:;|\\?)\\s*database(?:name)?\\s*=\\s*([^;?&]+)");
  private static final Pattern PATH_DBNAME =
          Pattern.compile("^jdbc:[a-z]+:(?:(?:[^:]+:)?//)?[^/;]+/([^?;]+)");

  public String getMainDatabaseUrl() {
    return mainDatabaseUrl;
  }

  public String getMainDatabaseUsername() {
    return mainDatabaseUsername;
  }

  public String getMainDatabasePassword() {
    return mainDatabasePassword;
  }

  /**
   * Create new data source for database (driver is selected by parsing URL),
   * check if we can connect to it and add it to dataSources
   * @param url
   * @param user
   * @param password
   * @param dataSourceOwnerValue
   * @return data source
   * @throws SQLException
   */
  private BasicDataSource createDataSourceAndCheckConnection(Long dataSourceOwnerValue, String url, String user, String password) throws SQLException {

    if (!StringUtils.isBlank(url)) {

      String driver;
      DB_TYPE dbType = DB_TYPE.getByConnectionString(url);

      if (dbType == null) {
        throw new SQLException("No driver found for connection string: " + url);
      }
      driver = dbType.getDriver();

      BasicDataSource dataSource;

      if (dataSourceOwnerValue == null || !dataSources.containsKey(dataSourceOwnerValue)) {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName(driver);
        dataSource.setUrl(url);
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        dataSource.setDefaultQueryTimeout(10);
        dataSource.setMaxTotal(200);
        dataSource.setMaxOpenPreparedStatements(100);
        dataSource.setMaxIdle(1);
        dataSource.setPoolPreparedStatements(true);
        dataSource.setDefaultAutoCommit(true);
        dataSource.setJmxName("org.apache.dbcp:DataSource=campaign" + dataSourceOwnerValue);
        if (dbType.equals(DB_TYPE.MS_SQL)) {
          dataSource.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
        }
      } else {
        dataSource = dataSources.get(dataSourceOwnerValue);
      }

      if (checkConnection(dataSource)) {
        if (dataSourceOwnerValue != null && !dataSources.containsKey(dataSourceOwnerValue)) {
          dataSources.put(dataSourceOwnerValue, dataSource);
        }
        return dataSource;
      } else {
        if (dataSourceOwnerValue != null) {
          dataSources.remove(dataSourceOwnerValue);
        }
        try {
          if (dataSource != null) {
            dataSource.close();
          }
        } catch (Exception e) {
          LOG.error("", e);
        }
        throw new SQLException("Can't get connection for connection string: " + url);
      }

    } else {
      throw new SQLException("Wrong connection parameters for url: " + url);
    }
  }

  /**
   * Extracts the main database name from the configured JDBC connection URL.
   *
   * <p>This method attempts to determine the database name portion of the main
   * JDBC URL used by the engine. It supports multiple URL patterns, such as
   * standard Microsoft SQL Server and path-based connection formats.</p>
   *
   * <p>The implementation uses the regular expression patterns
   * {@code MSSQL_DBNAME} and {@code PATH_DBNAME} to detect and capture
   * the database name. The first successful match is returned as a trimmed
   * string wrapped in an {@link Optional}. If the JDBC URL is null, exceeds
   * a reasonable length limit, or does not match any known pattern, an
   * empty {@link Optional} is returned.</p>
   *
   * @return an {@link Optional} containing the extracted database name if found,
   *         or an empty {@link Optional} if the name cannot be determined.
   */
  public Optional<String> extractMainDatabaseName() {
    String jdbcUrl=mainDatabaseUrl;
    if (jdbcUrl == null || jdbcUrl.length() > 2048) return Optional.empty();
    Matcher m = MSSQL_DBNAME.matcher(jdbcUrl);
    if (m.find()) return Optional.of(m.group(1).trim());
    m = PATH_DBNAME.matcher(jdbcUrl);
    if (m.find()) return Optional.of(m.group(1).trim());
    return Optional.empty();
  }

  /**
   * Get data source from dataSources by dataSourceOwnerValue
   * @param dataSourceOwnerValue
   * @param url
   * @param user
   * @param password
   * @return
   * @throws SQLException
   */
  public BasicDataSource getDataSource(Long dataSourceOwnerValue, String url, String user, String password) throws SQLException {
    if (dataSourceOwnerValue == null) {
      return createDataSourceAndCheckConnection(null, url, user, password);
    }
    if (dataSources.containsKey(dataSourceOwnerValue)) {
      return dataSources.get(dataSourceOwnerValue);
    }
    // FIX-333607: aynı campaign için tek oluşturma; kilit campaign bazında olduğu için yanıt vermeyen
    // bir müşteri DB'sine bağlantı denemesi diğer campaign'lerin datasource'unu bekletmez.
    synchronized (dataSourceCreationLocks.computeIfAbsent(dataSourceOwnerValue, key -> new Object())) {
      return dataSources.containsKey(dataSourceOwnerValue)
          ? dataSources.get(dataSourceOwnerValue)
          : createDataSourceAndCheckConnection(dataSourceOwnerValue, url, user, password);
    }
  }

  /**
   * Check connection for given dataSource
   * @param dataSource
   * @return
   */
  private boolean checkConnection(BasicDataSource dataSource) {
    boolean canConnect;
    Connection connection = null;
    try {
      connection = dataSource.getConnection();
      canConnect = connection != null && connection.isValid(5);
    } catch (Exception e) {
      canConnect = false;
    } finally {
      if (connection != null) {
        try {
          connection.close();
        } catch (Exception e) {
          LOG.error("", e);
        }
      }
    }
    return canConnect;
  }

  /**
   * Disconnect from database and clear pool
   * @param dataSourceOwnerValue
   * @throws SQLException
   */
  public void closeDataSource(Long dataSourceOwnerValue) throws SQLException {
    if (dataSourceOwnerValue != null && dataSources.containsKey(dataSourceOwnerValue)) {
      dataSources.get(dataSourceOwnerValue).close();
      dataSources.remove(dataSourceOwnerValue);
    }
  }

  /**
   * YOU SHOULD ALWAYS CLOSE RETRIEVED CONNECTIONS IN FINALLY!
   * @param dataSourceOwnerValue campaign'n id
   * @return connection
   * @throws SQLException
   */
  public Connection getConnection(Long dataSourceOwnerValue) throws SQLException {
    if (dataSourceOwnerValue != null && dataSources.containsKey(dataSourceOwnerValue)) {
      return dataSources.get(dataSourceOwnerValue).getConnection();
    }
    return null;
  }

  /**
   * Get array of strings for simple query to data source
   * @param sqlQuery
   * @param dataSourceOwnerValue
   * @param url
   * @param user
   * @param password
   * @return
   * @throws SQLException
   */
  public String[] selectTablesAndViews(String sqlQuery, Long dataSourceOwnerValue, String url, String user, String password) throws SQLException {
    BasicDataSource ds = null;
    Connection connection = null;

    try {
      ds = getDataSource(dataSourceOwnerValue, url, user, password);

      connection = ds.getConnection();
      if (connection == null) {
        throw new SQLException("Can't get connection for connection string: " + url);
      }

      List<String> tables = new LinkedList<>();

      //Safe sqlQuery is fully safe (no parameters)
      PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery);
      ResultSet rs = preparedStatement.executeQuery();

      while (rs.next()) {
        tables.add(rs.getString(1));
      }

      return tables.toArray(new String[0]);
    } finally {
      closeConnection(dataSourceOwnerValue, ds, connection);
    }
  }

  /**
   * Get two dimensional array (with two column) of strings for simple query to
   * data source
   * @param parametrizedQuery
   * @param dataSourceOwnerValue
   * @param url
   * @param user
   * @param password
   * @return
   * @throws SQLException
   */
  public String[][] selectColumnsWithTypes(ParametrizedQuery parametrizedQuery, Long dataSourceOwnerValue, String url, String user, String password) throws SQLException {
    BasicDataSource ds = null;
    Connection connection = null;

    try {
      ds = getDataSource(dataSourceOwnerValue, url, user, password);

      connection = ds.getConnection();
      if (connection == null) {
        throw new SQLException("Can't get connection for connection string: " + url);
      }

      List<String[]> tables = new LinkedList<>();

      PreparedStatement preparedStatement = connection.prepareStatement(parametrizedQuery.getQuery());
      if (!CollectionUtils.isEmpty(parametrizedQuery.getParameters())) {
        setParameters(preparedStatement, parametrizedQuery.getParameters());
      }
      ResultSet rs = preparedStatement.executeQuery();

      while (rs.next()) {
        tables.add(new String[] { rs.getString(1), rs.getString(2) });
      }

      return tables.toArray(new String[tables.size()][2]);
    } finally {
      closeConnection(dataSourceOwnerValue, ds, connection);
    }
  }

  /**
   * Check connection to client db
   * @param dataSourceOwnerValue
   * @return true if campaign has data source
   * @throws SQLException
   */
  public boolean checkConnection(Long dataSourceOwnerValue, String url, String user, String password) {
    if (dataSourceOwnerValue == null || !dataSources.containsKey(dataSourceOwnerValue)) {
      return false;
    }

    BasicDataSource ds = null;
    boolean wasAdded = false;
    if (dataSourceOwnerValue != null) {
      wasAdded = dataSources.containsKey(dataSourceOwnerValue);
    }

    try {
      ds = wasAdded ? getDataSource(dataSourceOwnerValue, url, user, password) : getDataSource(null, url, user, password);
      return true;
    } catch (SQLException e) {
      return false;
    } finally {
      try {
        if (!wasAdded && ds != null)
          ds.close();
      } catch (Exception e) {
        LOG.error("", e);
      }
    }
  }

  /**
   * Create data source for this campaign if dataSourceOwnerValue is not null
   * @param dataSourceOwnerValue
   * @return true if campaign has data source
   * @throws SQLException
   */
  public boolean connect(Long dataSourceOwnerValue, String url, String user, String password) throws SQLException {
    BasicDataSource ds = null;

    try {
      ds = getDataSource(dataSourceOwnerValue, url, user, password);
      return true;
    } finally {
      try {
        if (dataSourceOwnerValue == null && ds != null) {
          ds.close();
        }
      } catch (Exception e) {
        LOG.error("", e);
      }
    }
  }

  /**
   * Get array of rows for query to data source
   * @param parametrizedQuery
   * @param dataSourceOwnerValue
   * @param url
   * @param user
   * @param password
   * @return array or rows
   * @throws SQLException
   */
  public String[][] select(ParametrizedQuery parametrizedQuery, Integer limit, Long dataSourceOwnerValue, String url, String user, String password) throws SQLException {
    BasicDataSource ds = null;
    Connection connection = null;

    try {
      ds = getDataSource(dataSourceOwnerValue, url, user, password);
      connection = ds.getConnection();

      if (connection == null) {
        throw new SQLException("Can't get connection for connection string: " + url);
      }

      PreparedStatement preparedStatement = connection.prepareStatement(parametrizedQuery.getQuery());
      if (!CollectionUtils.isEmpty(parametrizedQuery.getParameters())) {
        setParameters(preparedStatement, parametrizedQuery.getParameters());
      }
      if (limit != null) {
        preparedStatement.setMaxRows(limit);
      }

      ResultSet rs = preparedStatement.executeQuery();
      int columns = rs.getMetaData().getColumnCount();
      List<String[]> result = new LinkedList<>();

      while (rs.next()) {
        String[] row = new String[columns];
        for (int j = 1; j <= columns; j++) {
          row[j - 1] = rs.getString(j);
        }
        result.add(row);
      }

      return result.toArray(new String[result.size()][]);
    } finally {
      closeConnection(dataSourceOwnerValue, ds, connection);
    }
  }

  /**
   * Get query result as JSONArray. Here we call via self reference in order to
   * support aspect's advices working
   * @param sqlQuery
   * @param dataSourceOwnerValue
   * @param url
   * @param user
   * @param password
   * @return array or rows
   * @throws SQLException
   */
  public JSONArray executeQuery(String sqlQuery, Long dataSourceOwnerValue, Integer limit, String url, String user, String password) throws SQLException {
    return self.executeParametrizedQuery(new ParametrizedQuery(sqlQuery, Collections.emptyList()), dataSourceOwnerValue, limit, url, user, password);
  }

  public JSONArray executeParametrizedQuery(ParametrizedQuery parametrizedQuery, Long dataSourceOwnerValue, Integer limit, String url, String user, String password) throws SQLException {
    BasicDataSource ds = null;
    Connection connection = null;
    try {
      ds = getDataSource(dataSourceOwnerValue, url, user, password);
      connection = ds.getConnection();
      if (connection == null) {
        throw new SQLException("Can't get connection for connection string: " + url);
      }
      connection.setAutoCommit(false);
      PreparedStatement preparedStatement = connection.prepareStatement(parametrizedQuery.getQuery());
      if (limit != null) {
        preparedStatement.setMaxRows(limit);
      }
      if (!CollectionUtils.isEmpty(parametrizedQuery.getParameters())) {
        setParameters(preparedStatement, parametrizedQuery.getParameters());
      }
      ResultSet rs = preparedStatement.executeQuery();
      int columns = rs.getMetaData().getColumnCount();
      JSONArray result = new JSONArray();

      while (rs.next()) {
        JSONObject row = new JSONObject();
        for (int j = 1; j <= columns; j++) {
          if (rs.getString(j) != null) {
            row.put(rs.getMetaData().getColumnName(j), rs.getString(j));
          }
        }
        if (row.keySet().isEmpty()) {
          continue;
        }
        result.put(row);
      }

      return result;
    } finally {
      closeConnection(dataSourceOwnerValue, ds, connection);
    }
  }

  /**
   * Task 327143: Query result'i JSONArray'e biriktirmeden satir satir tuketir.
   * Buyuk kampanya tablolarinda (DataManipulation fetch) heap'te dev gecici
   * koleksiyon olusmasini engeller. Satir semantigi JSONArray yolu ile birebir
   * aynidir (NULL kolon konmaz, tum kolonlari NULL satir atlanir).
   * Here we call via self reference in order to support aspect's advices working.
   *
   * @param maxRows tavan; asilirsa okuma kesilir. null veya Integer.MAX_VALUE
   *                pratikte sinirsizdir.
   * @return true ise sonuc tavana takilip kesilmistir
   */
  public boolean executeQueryStreaming(String sqlQuery, Long dataSourceOwnerValue, Integer maxRows, String url,
      String user, String password, Consumer<JSONObject> rowConsumer) throws SQLException {
    return self.executeParametrizedQuery(new ParametrizedQuery(sqlQuery, Collections.emptyList()),
        dataSourceOwnerValue, maxRows, url, user, password, rowConsumer);
  }

  /**
   * {@link #executeQueryStreaming} isleyicisi. Ad, aspect pointcut'lari
   * (DBEngineFileLogger, CampaignDatasourceMetricCollectorAspect) yeni yol icin
   * de calissin diye bilerek executeParametrizedQuery overload'udur.
   */
  public boolean executeParametrizedQuery(ParametrizedQuery parametrizedQuery, Long dataSourceOwnerValue,
      Integer maxRows, String url, String user, String password, Consumer<JSONObject> rowConsumer)
      throws SQLException {
    BasicDataSource ds = null;
    Connection connection = null;
    try {
      ds = getDataSource(dataSourceOwnerValue, url, user, password);
      connection = ds.getConnection();
      if (connection == null) {
        throw new SQLException("Can't get connection for connection string: " + url);
      }
      connection.setAutoCommit(false);
      PreparedStatement preparedStatement = connection.prepareStatement(parametrizedQuery.getQuery());
      if (maxRows != null) {
        // Tavan + 1: fazladan tek satir, kesme oldugunu anlamak icin okunur.
        preparedStatement.setMaxRows(maxRows == Integer.MAX_VALUE ? maxRows : maxRows + 1);
      }
      if (!CollectionUtils.isEmpty(parametrizedQuery.getParameters())) {
        setParameters(preparedStatement, parametrizedQuery.getParameters());
      }
      ResultSet rs = preparedStatement.executeQuery();
      return consumeResultSetRows(rs, maxRows, rowConsumer);
    } finally {
      closeConnection(dataSourceOwnerValue, ds, connection);
    }
  }

  private static boolean consumeResultSetRows(ResultSet rs, Integer maxRows, Consumer<JSONObject> rowConsumer)
      throws SQLException {
    ResultSetMetaData metaData = rs.getMetaData();
    int columns = metaData.getColumnCount();
    int delivered = 0;
    while (rs.next()) {
      JSONObject row = new JSONObject();
      for (int j = 1; j <= columns; j++) {
        String value = rs.getString(j);
        if (value != null) {
          row.put(metaData.getColumnName(j), value);
        }
      }
      if (row.keySet().isEmpty()) {
        continue;
      }
      if (maxRows != null && delivered >= maxRows) {
        return true;
      }
      rowConsumer.accept(row);
      delivered++;
    }
    return false;
  }

  /**
   * Handler for DML statements. Here we call via self reference in order to
   * support aspect's advices
   * @param sqlQuery
   * @param dataSourceOwnerValue
   * @param url
   * @param user
   * @param password
   * @return
   * @throws SQLException
   */
  public int executeUpdate(String sqlQuery, Long dataSourceOwnerValue, String url, String user, String password) throws SQLException {
    return self.executeBatchParametrizedUpdate(new ParametrizedQuery(sqlQuery, Collections.emptyList()),
        dataSourceOwnerValue, url, user, password);
  }

  public int executeBatchParametrizedUpdate(ParametrizedQuery parametrizedQuery, Long dataSourceOwnerValue,
                                            String url, String user, String password) throws SQLException {
    return executeBatchParametrizedUpdate(Collections.singletonList(parametrizedQuery), dataSourceOwnerValue, url, user, password);
  }

  public int executeBatchParametrizedUpdate(List<ParametrizedQuery> parametrizedQueries, Long dataSourceOwnerValue,
                                            String url, String user, String password) throws SQLException {
    BasicDataSource ds = null;
    Connection connection = null;
    try {
      ds = getDataSource(dataSourceOwnerValue, url, user, password);
      connection = ds.getConnection();
      if (connection == null) {
        throw new SQLException("Can't get connection for connection string: " + url);
      }
      connection.setAutoCommit(false);
      int rowsUpdated = 0;
      for (ParametrizedQuery parametrizedQuery : parametrizedQueries) {
        rowsUpdated = rowsUpdated + self.executeParametrizedUpdate(parametrizedQuery, dataSourceOwnerValue, connection);
      }
      connection.commit();
      connection.setAutoCommit(true);
      return rowsUpdated;
    } finally {
      closeConnection(dataSourceOwnerValue, ds, connection);
    }
  }

  /**
   * This method does not care neither about transaction state nor about connection closing, all such logic is up to method caller.
   * It is public in order to be proxied with Spring AOP.
   * @param parametrizedQuery the parametrized query to execute
   * @param dataSourceOwnerValue optional campaign id - required for overlying aspects
   * @param connection the database connection
   * @return count of updated rows
   * @throws SQLException
   */
  public int executeParametrizedUpdate(ParametrizedQuery parametrizedQuery, Long dataSourceOwnerValue, Connection connection) throws SQLException {
    PreparedStatement preparedStatement = connection.prepareStatement(parametrizedQuery.getQuery());
    if (!CollectionUtils.isEmpty(parametrizedQuery.getParameters())) {
      setParameters(preparedStatement, parametrizedQuery.getParameters());
    }
    return preparedStatement.executeUpdate();
  }

  private void setParameters(PreparedStatement statement, List<Object> params) throws SQLException {
    List<Object> flattenedParams = flattenParams(params);
    for (int i = 1; i <= flattenedParams.size(); i++) {
      Object param = flattenedParams.get(i - 1);
      statement.setObject(i, param);
    }
  }

  private List<Object> flattenParams(List<Object> params) {
    List<Object> flattenedParams = new ArrayList<Object>();
    params.forEach(param -> {
      if (param != null && param.getClass().isArray())
        flattenedParams.addAll(Arrays.asList((Object[]) param));
      else
        flattenedParams.add(param);
    });
    return flattenedParams;
  }

  private void closeConnection(Long dataSourceOwnerValue, BasicDataSource ds, Connection connection) {
    try {
      if (connection != null)
        connection.close();
      if (dataSourceOwnerValue == null && ds != null) {
        ds.close();
      }
    } catch (Exception e) {
      LOG.error("", e);
    }
  }


  /**
   * Executes a plain SQL query and returns all columns for each row,
   * explicitly including columns whose value is {@code NULL} in the database.
   *
   * <p>Unlike {@link #executeQuery}, this method does <b>not</b> skip null-valued
   * columns. Each null column is represented as {@link org.json.JSONObject#NULL}
   * in the returned {@link JSONObject}, ensuring the full schema is preserved
   * in the result even when a column carries no value.</p>
   *
   * <p>Intended for use cases where a complete column snapshot is required,
   * such as populating {@code dynamicFields2} in the interaction-notifier payload.</p>
   *
   * @param query                the SQL query to execute
   * @param dataSourceOwnerValue the campaign ID used to resolve the data source
   * @param limit                maximum number of rows to return; {@code null} means no limit
   * @param url                  JDBC connection URL
   * @param user                 database username
   * @param password             database password (plain-text, already decrypted)
   * @return a {@link JSONArray} of rows; each row contains every column,
   *         with null database values mapped to {@link org.json.JSONObject#NULL}
   * @throws SQLException if a database access error occurs or no connection can be obtained
   */
  public JSONArray executeQueryWithNulls(String query, Long dataSourceOwnerValue, Integer limit,
                                         String url, String user, String password) throws SQLException {
    BasicDataSource ds = null;
    Connection connection = null;
    try {
      ds = getDataSource(dataSourceOwnerValue, url, user, password);
      connection = ds.getConnection();
      if (connection == null) {
        throw new SQLException("Can't get connection for connection string: " + url);
      }

      // Disable auto-commit to stay consistent with the rest of the engine's query methods
      connection.setAutoCommit(false);

      PreparedStatement preparedStatement = connection.prepareStatement(query);

      // Apply row limit when provided to avoid fetching more data than needed
      if (limit != null) {
        preparedStatement.setMaxRows(limit);
      }

      ResultSet rs = preparedStatement.executeQuery();
      int columns = rs.getMetaData().getColumnCount();
      JSONArray result = new JSONArray();

      while (rs.next()) {
        JSONObject row = new JSONObject();
        for (int j = 1; j <= columns; j++) {
          String colName = rs.getMetaData().getColumnName(j);
          String value = rs.getString(j);

          // Map null DB values to JSONObject.NULL so that downstream consumers
          // (e.g. CallResultInMapper.mapDynamicFields2) can distinguish between
          // a column that is absent and one that is explicitly null in the DB.
          row.put(colName, value != null ? value : JSONObject.NULL);
        }

        result.put(row);
      }

      return result;
    } finally {
      // Always release the connection back to the pool regardless of outcome
      closeConnection(dataSourceOwnerValue, ds, connection);
    }
  }

}
