package com.powerdialer.util.supplier;

import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.when;
import java.util.Arrays;
import java.util.Map;

import java.io.IOException;

import org.junit.Test;
import org.mockito.Mockito;

import com.powerdialer.datamanipulation.supplier.MapDataSupplier;
import com.powerdialer.datamanipulation.supplier.datasource.TableDataSource;

public class MapDataSupplierTest {

  @Test
  public void givenDataSupplierWhenGetNextObjectThenObjectHaveEmptyValue() throws IOException {
    TableDataSource tableDataSourceMock = Mockito.mock(TableDataSource.class);
    when(tableDataSourceMock.getColumnNames()).thenReturn(new String[]{ "col1", "col2", "col3" });
    when(tableDataSourceMock.getNextRow()).thenReturn(Arrays.asList("val1", "val2"));
    MapDataSupplier mapDataSupplier = new MapDataSupplier(tableDataSourceMock);
    Map<String, String> nextObject = mapDataSupplier.getNextObject();
    assertEquals("val1", nextObject.get("col1"));
    assertEquals("val2", nextObject.get("col2"));
    assertEquals("", nextObject.get("col3"));
  }

}
