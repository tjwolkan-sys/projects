package com.powerdialer.util.supplier.datasource;

import java.util.List;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.powerdialer.datamanipulation.supplier.datasource.CsvDataSource;

public class CsvDataSourceTest {

  /**
   * Test initializing with specified column names
   * @throws IOException
   */
  @Test
  public void givenCsvSourceWhenReadRowThenHeaderIsInitialized() throws IOException {
    String csvRawSource = "columnName1,columnName2\n" +
      "cell11,cell12\n" +
      "cell21,cell22";
    ByteArrayInputStream csvInputStream = new ByteArrayInputStream(csvRawSource.getBytes("UTF-8"));
    CsvDataSource csvDataSource = new CsvDataSource(csvInputStream, true);
    List<String> nextRow = csvDataSource.getNextRow();
    Assert.assertEquals(2, csvDataSource.getColumnNames().length);
    Assert.assertArrayEquals(new String[]{ "columnName1", "columnName2" }, csvDataSource.getColumnNames());
  }

  /**
   * Test initializing with column names with index
   * @throws IOException
   */
  @Test
  public void givenCsvSourceWhenReadRowThenHeaderIsInitializedWithIndex() throws IOException {
    String csvRawSource = "columnName1,columnName2\n" +
      "cell11,cell12\n" +
      "cell21,cell22";
    ByteArrayInputStream csvInputStream = new ByteArrayInputStream(csvRawSource.getBytes("UTF-8"));
    CsvDataSource csvDataSource = new CsvDataSource(csvInputStream, false);
    List<String> nextRow = csvDataSource.getNextRow();
    Assert.assertEquals(2, csvDataSource.getColumnNames().length);
    Assert.assertArrayEquals(new String[]{ "0", "1" }, csvDataSource.getColumnNames());
  }

}
