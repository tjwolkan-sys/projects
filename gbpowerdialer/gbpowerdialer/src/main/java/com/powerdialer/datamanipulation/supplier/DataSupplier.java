package com.powerdialer.datamanipulation.supplier;

import java.util.Map;

import java.io.IOException;

import com.powerdialer.util.Constants;
import com.powerdialer.util.DatabaseUtil;

/**
 * Interface for supplying data from source such as JSON, XML, CSV, XLSX
 */
public abstract class DataSupplier<T> {

  protected int readCount = 0;
  private T currentObject;
  private boolean querySafeMode = false;
  private Constants.DB_TYPE dbType;

  /**
   * Get next object from source. For CSV and XLSX next object should represent an entire row.
   */
  public final T getNextObject() throws IOException {
    T nextObject = getNextObjectInternal();
    if (nextObject != null && querySafeMode && dbType != null) {
      DatabaseUtil.validate(dbType, (Map) nextObject);
    }
    currentObject = nextObject;
    readCount++;
    return nextObject;
  }

  public T getCurrentObject() {
    return currentObject;
  }

  protected abstract T getNextObjectInternal() throws IOException;

  public int getReadCount() {
    return readCount;
  }

  public void setQuerySafeMode(boolean querySafeMode) {
    this.querySafeMode = querySafeMode;
  }

  public void setDbType(Constants.DB_TYPE dbType) {
    this.dbType = dbType;
  }
}