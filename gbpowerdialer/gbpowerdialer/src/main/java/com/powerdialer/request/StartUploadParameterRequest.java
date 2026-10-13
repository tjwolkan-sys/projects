package com.powerdialer.request;

public class StartUploadParameterRequest {

  private boolean firstRowColumnNames;

  private boolean updateExistingData;

  private String dbIdColumnName;

  public boolean isFirstRowColumnNames() {
    return firstRowColumnNames;
  }

  public void setFirstRowColumnNames(boolean firstRowColumnNames) {
    this.firstRowColumnNames = firstRowColumnNames;
  }

  public boolean isUpdateExistingData() {
    return updateExistingData;
  }

  public void setUpdateExistingData(boolean updateExistingData) {
    this.updateExistingData = updateExistingData;
  }

  public String getDbIdColumnName() {
    return dbIdColumnName;
  }

  public void setDbIdColumnName(String dbIdColumnName) {
    this.dbIdColumnName = dbIdColumnName;
  }

  @Override
  public String toString() {
    return "StartUploadParameterRequest{" +
      "firstRowColumnNames=" + firstRowColumnNames +
      ", updateExistingData=" + updateExistingData +
      ", dbIdColumnName='" + dbIdColumnName + '\'' +
      '}';
  }
}
