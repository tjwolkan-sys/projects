package com.powerdialer.datamanipulation.supplier.datasource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.record.CellValueRecordInterface;
import org.apache.poi.hssf.record.LabelSSTRecord;
import org.apache.poi.hssf.record.NumberRecord;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.RowRecord;
import org.apache.poi.hssf.record.SSTRecord;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/**
 * Class for importing data from xls files. Its create new daemon thread which
 * run event processing and put data in ArrayBlockingQueue
 * @see HSSFEventFactory#processEvents
 */
public class XlsDataSource extends TableDataSource {

  private BlockingQueue<List<String>> rowQueue = new ArrayBlockingQueue<>(20);

  private boolean isFinishedReading;

  private Executor executor = Executors.newCachedThreadPool((r) -> {
    Thread t = Executors.defaultThreadFactory().newThread(r);
    t.setDaemon(true);
    return t;
  });

  public XlsDataSource(InputStream inputStream,
                       boolean firstRowColumnNames) throws IOException {
    super(firstRowColumnNames);
    isFinishedReading = false;
    start(inputStream);
  }

  private void start(InputStream inputStream) throws IOException {
    POIFSFileSystem poifs = new POIFSFileSystem(inputStream);
    InputStream din = poifs.createDocumentInputStream("Workbook");
    HSSFRequest req = new HSSFRequest();
    req.addListenerForAllRecords(new RecordListener());
    HSSFEventFactory hssfEventFactory = new HSSFEventFactory();
    executor.execute(() -> {
      try {
        hssfEventFactory.processEvents(req, din);
        isFinishedReading = true;
        din.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    });
  }

  @Override
  protected List<String> readRow() {
    if (isFinishedReading) {
      return rowQueue.poll();
    }
    try {
      return rowQueue.poll(100, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return null;
  }

  private class RecordListener implements HSSFListener {

    private SSTRecord sstrec;
    private int maxCol;
    private List<String> row = new ArrayList<>();

    /**
     * This method listens for incoming records and handles them as required.
     * @param record The record that was found while reading.
     */
    @Override
    public void processRecord(Record record) {
      switch (record.getSid()) {
        case RowRecord.sid:
          RowRecord rowrec = (RowRecord) record;
          maxCol = Math.max(rowrec.getLastCol() - 1, maxCol);
          break;
        case NumberRecord.sid:
          NumberRecord numrec = (NumberRecord) record;
          row.add(String.valueOf((long) numrec.getValue()));
          break;
        case SSTRecord.sid:
          sstrec = (SSTRecord) record;
          break;
        case LabelSSTRecord.sid:
          LabelSSTRecord lrec = (LabelSSTRecord) record;
          row.add(sstrec.getString(lrec.getSSTIndex()).toString());
          break;
        default:
          break;
      }
      if (record instanceof CellValueRecordInterface) {
        int currentCol = ((CellValueRecordInterface) record).getColumn();
        if (currentCol == maxCol) {
          try {
            rowQueue.put(row);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          row = new ArrayList<>();
        }
      }
    }
  }
}
