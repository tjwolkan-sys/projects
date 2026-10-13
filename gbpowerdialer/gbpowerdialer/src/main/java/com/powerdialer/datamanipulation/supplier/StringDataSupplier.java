package com.powerdialer.datamanipulation.supplier;

import java.util.List;

import java.io.IOException;

import com.powerdialer.datamanipulation.supplier.datasource.TableDataSource;

/**
 * Implementation for importing data in String from TableDataSource file. Only first cell of
 * each row will be imported.
 */
public class StringDataSupplier extends DataSupplier<String> {

  private final TableDataSource tableDataSource;

  public StringDataSupplier(TableDataSource tableDataSource) throws IOException {
    this.tableDataSource = tableDataSource;
  }

  @Override
  protected String getNextObjectInternal() throws IOException {
    List<String> nextRow = tableDataSource.getNextRow();
    if (nextRow != null) {
      if (nextRow.size() >= 1) {
        return nextRow.get(0);
      } else {
        return "";
      }
    } else {
      return null;
    }
  }
}
