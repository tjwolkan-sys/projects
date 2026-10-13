package com.powerdialer.util.supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Map;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.powerdialer.datamanipulation.supplier.MapDataSupplier;
import com.powerdialer.datamanipulation.supplier.datasource.CsvDataSource;

/**
 * Test for CsvMapDataSupplier class
 */
public class CsvMapDataSupplierTest {

  /**
   * Test successful reading from csv source
   */
  @Test
  public void givenCsvDataSourceWhenGenNextObjectThenReadRowsSequentially() throws IOException {
    String csv = "col1,col2\n" + "val1, val2\n" + "1,\"2\"\n";
    MapDataSupplier csvMapDataSupplier = new MapDataSupplier(new CsvDataSource(IOUtils.toInputStream(csv), true));
    Map<String, String> nextObject = csvMapDataSupplier.getNextObject();
    assertTrue(nextObject.containsKey("col1"));
    assertEquals("val1", nextObject.get("col1"));
    nextObject = csvMapDataSupplier.getNextObject();
    assertEquals("2", nextObject.get("col2"));
  }

  /**
   * Test reading from csv source with different row length
   */
  @Test
  public void givenCsvDataSourceWhenGetNextObjectThenReadOnlyNamedColumns() throws IOException {
    String csv = "col1\n" + "val1,val2\n";
    MapDataSupplier csvMapDataSupplier = new MapDataSupplier(new CsvDataSource(IOUtils.toInputStream(csv), true));
    Map<String, String> nextObject = csvMapDataSupplier.getNextObject();
    assertEquals(1, nextObject.size());
    assertEquals("val1", nextObject.get("col1"));
  }

  /**
   * Test reading csv with comma in value
   * @throws IOException
   */
  @Test
  public void givenCsvSourceWithCommaWhenGetNextObjectThenValueContainsComma() throws IOException {
    String csv = "col1,col2\n" + "\"1,2\",2\n" + "val1, val2\n";
    MapDataSupplier csvMapDataSupplier = new MapDataSupplier(new CsvDataSource(IOUtils.toInputStream(csv), true));
    Map<String, String> nextObject = csvMapDataSupplier.getNextObject();
    assertTrue(nextObject.containsKey("col1"));
    assertEquals("1,2", nextObject.get("col1"));
  }

  /**
   * Test reading csv with double-quotes in value
   * @throws IOException
   */
  @Test
  public void givenCsvSourceWithDoubleQuotesWhenGetNextObjectThenValueContainsDoubleQuotes() throws IOException {
    String csv = "col1,col2\n" + "\"\"\"quotedValue\"\"\",2\n" + "val1, val2\n";
    MapDataSupplier csvMapDataSupplier = new MapDataSupplier(new CsvDataSource(IOUtils.toInputStream(csv), true));
    Map<String, String> nextObject = csvMapDataSupplier.getNextObject();
    assertTrue(nextObject.containsKey("col1"));
    assertEquals("\"quotedValue\"", nextObject.get("col1"));
  }
}
