package com.powerdialer.datamanipulation.supplier.datasource;

import java.util.List;

import org.springframework.util.StringUtils;

/**
 * Contract for reading data from table-like dat sources (xls, xlsx or csv
 * format)
 */
public abstract class TableDataSource {

  private final boolean firstRowColumnNames;
  private String[] columnNames;

  public TableDataSource(boolean firstRowColumnNames) {
    this.firstRowColumnNames = firstRowColumnNames;
  }

  private String[] generateColumnNames(int columnsCount) {
    String[] columnNames = new String[columnsCount];
    for (int i = 0; i < columnsCount; i++) {
      columnNames[i] = Integer.toString(i);
    }
    return columnNames;
  }

  public String[] getColumnNames() {
    return columnNames;
  }

  /**
   * Supply row and init column names
   * @return
   */
  public List<String> getNextRow() {
    List<String> row = readRow();
    if (columnNames == null) {
      initColumnNames(row);
      if (firstRowColumnNames) {
        row = readRow();
      }
    }
    return row;
  }

  public void initColumnNames(List<String> firstRow) {
    if (firstRowColumnNames) {
      columnNames = new String[firstRow.size()];
      for (int i = 0; i < columnNames.length; i++) {
        columnNames[i] = StringUtils.isEmpty(firstRow.get(i)) ? Integer.toString(i) : firstRow.get(i);
      }
    } else {
      columnNames = generateColumnNames(firstRow.size());
    }
  }

  /**
   * Read row table datasource
   * @return
   */
  protected abstract List<String> readRow();

}
