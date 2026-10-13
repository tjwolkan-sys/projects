package com.powerdialer.datamanipulation.supplier.datasource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import java.io.InputStream;

import com.monitorjbl.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Class for importing data from xlsx files
 */
public class XlsxDataSource<T> extends TableDataSource {

  private Iterator<Row> rowIterator;

  public XlsxDataSource(InputStream inputStream,
                        boolean firstRowColumnNames) {
    super(firstRowColumnNames);
    Workbook workbook = StreamingReader.builder().rowCacheSize(20).open(inputStream);
    Sheet sheet = workbook.iterator().next();
    rowIterator = sheet.iterator();
  }

  @Override
  protected List<String> readRow() {
    if (rowIterator == null || !rowIterator.hasNext())
      return null;
    Row nativeRow = rowIterator.next();
    List<String> nextRow = new ArrayList<>();
    for (Cell cell : nativeRow) {
      if (cell == null) {
        nextRow.add("");
      } else {
        if (cell.getColumnIndex() > nextRow.size()) {
          fillWithNulls(nextRow, cell.getColumnIndex() - nextRow.size());
        }
        nextRow.add(cell.getStringCellValue());
      }
    }
    return nextRow;
  }

  private void fillWithNulls(List<?> list, int count) {
    for (int i = 0; i < count; i++) {
      list.add(null);
    }
  }

}
