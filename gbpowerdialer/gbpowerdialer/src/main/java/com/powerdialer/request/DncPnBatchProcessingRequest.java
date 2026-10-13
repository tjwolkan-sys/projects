package com.powerdialer.request;

import java.util.Map;

public class DncPnBatchProcessingRequest {

  private Map<String, Object> filters;

  private String type;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Map<String, Object> getFilters() {
    return filters;
  }

  public void setFilters(Map<String, Object> filters) {
    this.filters = filters;
  }

  @Override
  public String toString() {
    return "DncPnBatchProcessingRequest{" +
      "filters=" + filters +
      ", type='" + type + '\'' +
      '}';
  }
}
