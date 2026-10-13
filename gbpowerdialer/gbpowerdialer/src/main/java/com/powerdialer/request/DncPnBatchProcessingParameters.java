package com.powerdialer.request;

import java.util.Objects;

public class DncPnBatchProcessingParameters {

  private boolean checkboxResult;
  private String columnParameter;
  private String expiredColumnParameter;

  public boolean isCheckboxResult() {
    return checkboxResult;
  }
  public void setCheckboxResult(boolean checkboxResult) {
    this.checkboxResult = checkboxResult;
  }

  public String getColumnParameter() {
    return columnParameter;
  }
  public void setColumnParameter(String columnParameter) {
    this.columnParameter = columnParameter;
  }

  public String getExpiredColumnParameter() { return expiredColumnParameter; }
  public void setExpiredColumnParameter(String expiredColumnParameter) { this.expiredColumnParameter = expiredColumnParameter; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DncPnBatchProcessingParameters that = (DncPnBatchProcessingParameters) o;
    return checkboxResult == that.checkboxResult &&
      Objects.equals(columnParameter, that.columnParameter);
  }

  @Override
  public int hashCode() {
    return Objects.hash(checkboxResult, columnParameter);
  }

  @Override
  public String toString() {
    return "DncPnBatchProcessingParameters{" +
      "checkboxResult=" + checkboxResult +
      "expiredColumnParameter=" + expiredColumnParameter +
      ", columnParameter='" + columnParameter + '\'' +
      '}';
  }
}
