package com.powerdialer.request;

import java.util.List;
import java.util.Map;

import com.powerdialer.util.Constants;

public class ProcessUploadParameterRequest {

  private Constants.PHONE_NUMBERS_ANTI_DUPLICATION antiDuplication = Constants.PHONE_NUMBERS_ANTI_DUPLICATION.DISABLED;

  private Map<String, String> remappedColumns;

  private List<String> hiddenColumns;

  public Constants.PHONE_NUMBERS_ANTI_DUPLICATION getAntiDuplication() {
    return antiDuplication;
  }

  public void setAntiDuplication(Constants.PHONE_NUMBERS_ANTI_DUPLICATION antiDuplication) {
    this.antiDuplication = antiDuplication;
  }

  public Map<String, String> getRemappedColumns() {
    return remappedColumns;
  }

  public void setRemappedColumns(Map<String, String> remappedColumns) {
    this.remappedColumns = remappedColumns;
  }

  public List<String> getHiddenColumns() {
    return hiddenColumns;
  }

  public void setHiddenColumns(List<String> hiddenColumns) {
    this.hiddenColumns = hiddenColumns;
  }
}
