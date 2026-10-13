package com.powerdialer.engine;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.hibernate.mapping.Collection;

import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import com.powerdialer.engine.db.DBEngine;
import com.powerdialer.model.ParametrizedQuery;

import junit.framework.TestCase;

/**
 * Test for DNBEngine class
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({DBEngine.class})
@PowerMockIgnore({"javax.management.*", "jdk.internal.reflect.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class DBEngineTest {

  private DBEngine dbEngine;

  @Before
  public void init() {
    dbEngine = PowerMockito.spy(new DBEngine());
    Whitebox.setInternalState(dbEngine, "self", dbEngine);
  }

  /**
   * Test check connection with null parameters
   */
  @Test
  public void checkConnectionWithNullParametersTest() {
    assertFalse(dbEngine.checkConnection(null, null, null, null));
  }

  /**
   * Test check connection if dataSources doesn't have contain key
   */
  @Test
  public void checkConnectionWithoutDataSourcesTest() {
    Map<Long, BasicDataSource> dataSources = new HashMap<>();
    dataSources = spy(dataSources);
    Whitebox.setInternalState(dbEngine, "dataSources", dataSources);
    assertFalse(dbEngine.checkConnection(12L, null, null, null));
  }

  /**
   * Test check connection if dataSources contain key
   */
  @Test
  public void checkConnectionWithDataSourceTest() throws SQLException {
    Map<Long, BasicDataSource> dataSources = new HashMap<>();
    dataSources.put(12L, null);
    dataSources = spy(dataSources);
    Whitebox.setInternalState(dbEngine, "dataSources", dataSources);
    BasicDataSource basicDataSource = PowerMockito.mock(BasicDataSource.class);

    DBEngine dbe = spy(DBEngine.class);
    doReturn(basicDataSource).when(dbe).getDataSource(12L, "", "", "");

    basicDataSource.close();

    assertTrue(dbEngine.checkConnection(12L, "", "", ""));
  }

  /**
   * Test check connection if connection return SQLException
   */
  @Test
  public void checkConnectionFalseTest() throws SQLException {
    Map<Long, BasicDataSource> dataSources = new HashMap<>();
    dataSources.put(12L, null);
    dataSources = spy(dataSources);
    Whitebox.setInternalState(dbEngine, "dataSources", dataSources);

    doThrow(new SQLException()).when(dbEngine).getDataSource(12L, "", "", "");

    assertFalse(dbEngine.checkConnection(12L, "", "", ""));
  }

  /**
   * Test connection if all parameters is null
   * @throws SQLException
   */
  @Test(expected = SQLException.class)
  public void connectNullTest() throws SQLException {
    doThrow(new SQLException()).when(dbEngine).getDataSource(null, null, null, null);

    dbEngine.connect(null, null, null, null);
  }

  /**
   * Test connection if dataSourceOwnerValue null but BasicDataSource not null
   * BasicDataSource can be close!
   * @throws SQLException
   */
  @Test
  public void connectFalseAndCloseBasicDataSourceTest() throws SQLException {
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);
    PowerMockito.doReturn(bds).when(dbEngine).getDataSource(null, "", "", "");

    assertTrue(dbEngine.connect(null, "", "", ""));
  }

  /**
   * Test connection if url, user name and password is empty
   * @throws SQLException
   */
  @Test(expected = SQLException.class)
  public void connectFalseTest() throws SQLException {
    doThrow(new SQLException()).when(dbEngine).getDataSource(12L, "", "", "");

    dbEngine.connect(12L, "", "", "");
  }

  /**
   * Test correct connection
   * @throws SQLException
   */
  @Test
  public void connectTrueTest() throws SQLException {
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);

    doReturn(bds).when(dbEngine).getDataSource(anyLong(), anyString(), anyString(), anyString());
    Assert.assertTrue(dbEngine.connect(1L, "some url", "some name", "some password"));
  }

  /**
   * Test close dataSource when dataSourceOwnerValue is null
   * @throws SQLException
   */
  @Test
  public void closeDataSourceNullTest() throws SQLException {
    Boolean canClose;
    dbEngine.closeDataSource(null);
    canClose = true;

    assertEquals(true, canClose);
  }

  /**
   * Test close dataSource when dataSource is empty
   * @throws SQLException
   */
  @Test
  public void closeDataSourceEmptyTest() throws SQLException {
    Boolean canClose;
    dbEngine.closeDataSource(-1L);
    canClose = true;

    assertEquals(true, canClose);
  }

  /**
   * Close correct dataSource
   * @throws SQLException
   */
  @Test
  public void closeDataSourceTrueTest() throws SQLException {
    Map<Long, BasicDataSource> dataSources = new HashMap<>();
    BasicDataSource bds = spy(BasicDataSource.class);
    dataSources.put(1L, bds);

    Whitebox.setInternalState(dbEngine, "dataSources", dataSources);

    dbEngine.closeDataSource(1L);
    verify(bds).close();
  }

  /**
   * Test getConnection when argument is null
   * @throws SQLException
   */
  @Test
  public void getConnectionNullTest() throws SQLException {
    assertEquals(true, dbEngine.getConnection(null) == null);
  }

  /**
   * Test getConnection when dataSource is empty
   * @throws SQLException
   */
  @Test
  public void getConnectionEmptyTest() throws SQLException {
    assertEquals(true, (dbEngine.getConnection(-1L) == null));
  }

  /**
   * Test correct getConnection
   * @throws SQLException
   */
  @Test
  public void getConnectionTrueTest() throws SQLException {
    Map<Long, BasicDataSource> dataSources = new HashMap<>();
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);
    Connection connection = PowerMockito.mock(Connection.class);

    dataSources.put(12L, bds);
    dataSources = spy(dataSources);
    doReturn(true).when(dataSources).containsKey(12L);
    Whitebox.setInternalState(dbEngine, "dataSources", dataSources);
    doReturn(connection).when(bds).getConnection();

    TestCase.assertTrue(dbEngine.getConnection(12L) != null);
  }

  /**
   * Test getDataSource when all arguments is null
   * @throws SQLException
   */
  @Test(expected = SQLException.class)
  public void getDataSourceNullTest() throws SQLException {
    dbEngine.getDataSource(null, null, null, null);
  }

  /**
   * Test getDataSource when utl, user name and password is empty
   * @throws SQLException
   */
  @Test(expected = SQLException.class)
  public void getDataSourceFalseTest() throws SQLException {
    dbEngine.getDataSource(-2L, "", "", "");
  }

  /**
   * Test getDataSource when dataSource contains existing BasicDataSource
   * @throws SQLException
   */
  @Test
  public void getDataSourceContainsKeyTest() throws SQLException {
    Map<Long, BasicDataSource> dataSources = new HashMap<>();
    BasicDataSource bds = spy(BasicDataSource.class);
    dataSources.put(1L, bds);

    Whitebox.setInternalState(dbEngine, "dataSources", dataSources);

    assertEquals(bds, dbEngine.getDataSource(1L, "", "", ""));
  }

  /**
   * Test correct getDataSource
   * @throws Exception
   */
  @Test
  public void getDataSourceTrueTest() throws Exception {
    Map<Long, BasicDataSource> dataSources = new HashMap<>();
    BasicDataSource bds = spy(BasicDataSource.class);

    Whitebox.setInternalState(dbEngine, "dataSources", dataSources);

    PowerMockito.doReturn(bds).when(dbEngine, "createDataSourceAndCheckConnection", anyLong(), anyString(), anyString(), anyString());
    assertEquals(bds, dbEngine.getDataSource(1L, "", "", ""));
  }

  /**
   * Test selectTablesAndViews with null sqlQuery
   */
  @Test(expected = NullPointerException.class)
  public void selectColumnNullTest() throws SQLException {
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);

    PowerMockito.doReturn(bds).when(dbEngine).getDataSource(anyLong(), anyString(), anyString(), anyString());
    PowerMockito.doReturn(PowerMockito.mock(Connection.class)).when(bds).getConnection();

    dbEngine.selectTablesAndViews(null, 1L, "", "", "");
  }

  /**
   * Test selectTablesAndViews with empty sqlQuery
   * @throws SQLException
   */
  @Test(expected = SQLException.class)
  public void selectColumnEmptyTest() throws SQLException {
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);

    PowerMockito.doReturn(bds).when(dbEngine).getDataSource(anyLong(), anyString(), anyString(), anyString());
    PowerMockito.doReturn(null).when(bds).getConnection();

    dbEngine.selectTablesAndViews("", 1L, "", "", "");
  }

/**
   * Test selectTablesAndViews with connection null
   * @throws SQLException
   */
  @Test(expected = NullPointerException.class)
  public void selectColumnIfConnectionNullTest() throws SQLException {
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);

    PowerMockito.doReturn(bds).when(dbEngine).getDataSource(anyLong(), anyString(), anyString(), anyString());
    PowerMockito.doReturn(PowerMockito.mock(Connection.class)).when(bds).getConnection();

    dbEngine.selectTablesAndViews("", 1L, "", "", "");
  }

  /**
   * Test correct selectTablesAndViews
   * @throws SQLException
   */
  @Test
  public void selectColumnTrueTest() throws SQLException {
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);
    Connection connection = PowerMockito.mock(Connection.class);
    PreparedStatement ps = PowerMockito.mock(PreparedStatement.class);
    ResultSet rs = PowerMockito.mock(ResultSet.class);

    PowerMockito.doReturn(bds).when(dbEngine).getDataSource(anyLong(), anyString(), anyString(), anyString());
    PowerMockito.doReturn(connection).when(bds).getConnection();
    PowerMockito.doReturn(ps).when(connection).prepareStatement(anyString());
    PowerMockito.doReturn(rs).when(ps).executeQuery();
    PowerMockito.doReturn(PowerMockito.mock(String.class)).when(rs).getString(1);
    when(rs.next()).thenReturn(true).thenReturn(false);

    Assert.assertNotEquals(0, dbEngine.selectTablesAndViews("", 1L, "", "", "").length);
  }

  /**
   * Test select row with null sqlQuery
   * @throws SQLException
   */
  @Test(expected = NullPointerException.class)
  public void queryNullTest() throws SQLException {
    BasicDataSource basicDataSource = PowerMockito.mock(BasicDataSource.class);
    Connection connection = PowerMockito.mock(Connection.class);

    PowerMockito.doReturn(basicDataSource).when(dbEngine).getDataSource(0L, "", "", "");
    PowerMockito.doReturn(connection).when(basicDataSource).getConnection();

    dbEngine.select(null, null, 0L, "", "", "");
  }

  /**
   * Test select row with null connection
   * @throws SQLException
   */
  @Test(expected = SQLException.class)
  public void queryNullConnectionTest() throws SQLException {
    BasicDataSource basicDataSource = PowerMockito.mock(BasicDataSource.class);

    PowerMockito.doReturn(basicDataSource).when(dbEngine).getDataSource(0L, "", "", "");
    PowerMockito.doReturn(null).when(basicDataSource).getConnection();

    dbEngine.select(null, null, 0L, "", "", "");
  }

  /**
   * Test select row with empty sqlQuery
   * @throws SQLException
   */
  @Test(expected = SQLException.class)
  public void queryEmptyTest() throws SQLException {
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);
    Connection connection = PowerMockito.mock(Connection.class);

    PowerMockito.doReturn(bds).when(dbEngine).getDataSource(anyLong(), anyString(), anyString(), anyString());
    PowerMockito.doReturn(connection).when(bds).getConnection();
    doThrow(new SQLException()).when(connection).prepareStatement(anyString());

    dbEngine.select(new ParametrizedQuery("", Collections.EMPTY_LIST), null, -1L, "", "", "");
  }

  /**
   * Test correct select row
   * @throws SQLException
   */
  @Test
  public void queryTrueTest() throws Exception {
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);
    Connection connection = PowerMockito.mock(Connection.class);
    PreparedStatement ps = PowerMockito.mock(PreparedStatement.class);
    ResultSet rs = PowerMockito.mock(ResultSet.class);
    ResultSetMetaData metaData = PowerMockito.mock(ResultSetMetaData.class);

    PowerMockito.doReturn(bds).when(dbEngine).getDataSource(anyLong(), anyString(), anyString(), anyString());
    PowerMockito.doReturn(connection).when(bds).getConnection();
    PowerMockito.doReturn(ps).when(connection).prepareStatement(anyString());
    PowerMockito.doReturn(rs).when(ps).executeQuery();

    PowerMockito.doReturn(metaData).when(rs).getMetaData();
    PowerMockito.doReturn(0).when(metaData).getColumnCount();
    PowerMockito.doReturn(PowerMockito.mock(String.class)).when(rs).getString(1);
    when(rs.next()).thenReturn(true).thenReturn(false);

    PowerMockito.doNothing().when(dbEngine, "closeConnection", 1L, bds, connection);

    Assert.assertNotEquals(0, dbEngine.select(new ParametrizedQuery("", Collections.EMPTY_LIST), null, 1L, "", "", "").length);
  }

  /**
   * Test correct select row
   * @throws Exception
   */
  @Test
  public void queryTrueTest2() throws Exception {
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);
    Connection connection = PowerMockito.mock(Connection.class);
    PreparedStatement ps = PowerMockito.mock(PreparedStatement.class);
    ResultSet rs = PowerMockito.mock(ResultSet.class);
    ResultSetMetaData metaData = PowerMockito.mock(ResultSetMetaData.class);

    PowerMockito.doReturn(bds).when(dbEngine).getDataSource(anyLong(), anyString(), anyString(), anyString());
    PowerMockito.doReturn(connection).when(bds).getConnection();
    PowerMockito.doReturn(ps).when(connection).prepareStatement(anyString());
    PowerMockito.doReturn(rs).when(ps).executeQuery();

    PowerMockito.doReturn(metaData).when(rs).getMetaData();
    PowerMockito.doReturn(0).when(metaData).getColumnCount();
    PowerMockito.doReturn(PowerMockito.mock(String.class)).when(rs).getString(1);
    when(rs.next()).thenReturn(true).thenReturn(false);

    PowerMockito.doNothing().when(dbEngine, "closeConnection", 1L, bds, connection);

    Assert.assertNotEquals(0, dbEngine.select(new ParametrizedQuery("", Collections.EMPTY_LIST), 5, 1L, "", "", "").length);
  }

  /**
   * Test correct execute update
   * @throws SQLException
   */
  @Test
  public void executeUpdateTest() throws SQLException {
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);
    Connection connection = PowerMockito.mock(Connection.class);
    PreparedStatement ps = PowerMockito.mock(PreparedStatement.class);

    PowerMockito.doReturn(bds).when(dbEngine).getDataSource(anyLong(), anyString(), anyString(), anyString());
    PowerMockito.doReturn(connection).when(bds).getConnection();
    PowerMockito.doReturn(ps).when(connection).prepareStatement(anyString());
    PowerMockito.doReturn(PowerMockito.mock(Integer.class)).when(ps).executeUpdate();

    assertEquals(0, dbEngine.executeUpdate("some query", 1L, "some url", "some user name", "some password"));
  }

  /**
   * Test execute update when connection null
   * @throws SQLException
   */
  @Test(expected = SQLException.class)
  public void executeUpdateWhenConnectionNullTest() throws SQLException {
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);

    PowerMockito.doReturn(bds).when(dbEngine).getDataSource(anyLong(), anyString(), anyString(), anyString());
    PowerMockito.doReturn(null).when(bds).getConnection();

    dbEngine.executeUpdate("some query", 1L, "some url", "some user name", "some password");
  }

  /**
   * Test executeQuery when pass wrong parameters and getDataSource return SQLException
   * @throws SQLException
   */
  @Test(expected = SQLException.class)
  public void executeQueryCantGetBasicDataSourceTest() throws SQLException {
    doThrow(new SQLException()).when(dbEngine).getDataSource(1L, "", "", " ");

    dbEngine.executeQuery("", 1L, 0, "", "", "");
  }

  /**
   * Test executeQuery when can't make connect to BasicDataSource
   * @throws SQLException
   */
  @Test(expected = SQLException.class)
  public void executeQueryCantConnectToBasicDataSource() throws SQLException {
    BasicDataSource bds = PowerMockito.mock(BasicDataSource.class);

    PowerMockito.doReturn(bds).when(dbEngine).getDataSource(1L, "", "", "");
    PowerMockito.doReturn(null).when(bds).getConnection();

    dbEngine.executeQuery("", 1L, 0, "", "", "");
  }

  /**
   * Test executeQuery with null sqlQuery
   * @throws SQLException
   */
  @Test(expected = NullPointerException.class)
  public void executeQueryNullQueryTest() throws SQLException {
    BasicDataSource basicDataSource = PowerMockito.mock(BasicDataSource.class);

    PowerMockito.doReturn(basicDataSource).when(dbEngine).getDataSource(0L, "", "", "");

    Connection connection = PowerMockito.mock(Connection.class);
    PowerMockito.doReturn(connection).when(basicDataSource).getConnection();

    dbEngine.executeQuery(null, 0L, null, "", "", "");
  }

  /**
   * Test getColumnCount in executeQuery
   * @throws Exception
   */
  @Test()
  public void executeQueryGetColumnCountTest() throws Exception {
    BasicDataSource basicDataSource = PowerMockito.mock(BasicDataSource.class);
    Connection connection = PowerMockito.mock(Connection.class);
    PreparedStatement preparedStatement = PowerMockito.mock(PreparedStatement.class);
    ResultSet resultSet = PowerMockito.mock(ResultSet.class);
    ResultSetMetaData resultSetMetaData = PowerMockito.mock(ResultSetMetaData.class);

    PowerMockito.doReturn(basicDataSource).when(dbEngine).getDataSource(0L, "", "", "");

    PowerMockito.doReturn(connection).when(basicDataSource).getConnection();

    PowerMockito.doReturn(preparedStatement).when(connection).prepareStatement(anyString());

    PowerMockito.doReturn(resultSet).when(preparedStatement).executeQuery();

    PowerMockito.doReturn(resultSetMetaData).when(resultSet).getMetaData();
    PowerMockito.doReturn(1).when(resultSetMetaData).getColumnCount();

    PowerMockito.doReturn("columnName").when(resultSetMetaData).getColumnName(1);
    PowerMockito.doReturn(PowerMockito.mock(String.class)).when(resultSet).getString(1);
    doReturn(true).doReturn(true).doReturn(false).when(resultSet).next();

    PowerMockito.doNothing().when(dbEngine, "closeConnection", 0L, basicDataSource, connection);

    Assert.assertEquals(2, dbEngine.executeQuery("", 0L, null, "", "", "").length());
  }

  @Test(expected = SQLException.class)
  public void whenFailConnectionSelectColumnsWithTypesTest() throws SQLException {
    BasicDataSource basicDataSource = PowerMockito.mock(BasicDataSource.class);

    doReturn(basicDataSource).when(dbEngine).getDataSource(anyLong(), anyString(), anyString(), anyString());

    dbEngine.selectColumnsWithTypes(any(), anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  public void selectColumnsWithTypesTest() throws SQLException {
    BasicDataSource basicDataSource = Mockito.mock(BasicDataSource.class);
    Connection connection = Mockito.mock(Connection.class);
    PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
    ResultSet resultSet = Mockito.mock(ResultSet.class);

    doReturn(basicDataSource).when(dbEngine).getDataSource(1L, "", "", "");
    doReturn(connection).when(basicDataSource).getConnection();

    doReturn(preparedStatement).when(connection).prepareStatement("");
    doReturn(resultSet).when(preparedStatement).executeQuery();

    List<String[]> tables = new LinkedList<>();

    String str1 = "1";
    String str2 = "2";

    Mockito.doReturn(str1).when(resultSet).getString(1);
    Mockito.doReturn(str2).when(resultSet).getString(2);
    when(resultSet.next()).thenReturn(true).thenReturn(false);

    tables.add(new String[] {str1, str2});

    String [][] s = tables.toArray(new String[tables.size()][2]);

    String [][] s2 = dbEngine.selectColumnsWithTypes(new ParametrizedQuery("", Collections.EMPTY_LIST), 1L, "", "", "");

    Assert.assertArrayEquals(s, s2);
  }
}
