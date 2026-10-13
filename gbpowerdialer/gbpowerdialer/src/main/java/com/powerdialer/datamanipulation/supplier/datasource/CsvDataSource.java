package com.powerdialer.datamanipulation.supplier.datasource;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import com.google.common.collect.Iterables;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Class for importing data from csv files
 */
public class CsvDataSource extends TableDataSource {

  private Iterator<CSVRecord> rowsIterator;

  public CsvDataSource(InputStream inputStream,
                       boolean firstRowColumnNames) throws IOException {
    super(firstRowColumnNames);
    rowsIterator = CSVParser.parse(inputStream, Charset.defaultCharset(), CSVFormat.RFC4180).iterator();
  }

  @Override
  protected List<String> readRow() {
    if (rowsIterator.hasNext()) {
      CSVRecord next = rowsIterator.next();
      if (next != null) {
        return Arrays.asList(Iterables.toArray(next, String.class));
      }
    }
    return null;
  }

}
