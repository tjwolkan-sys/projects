package com.powerdialer.dto;

import org.dozer.Mapping;

public class SortFieldValueListDTO extends BaseEntityDTO {

  private String value;
  private int priority;
  private Long sortField;

  @Mapping("sortField.id")
  public Long getSortField() {
    return sortField;
  }

  public void setSortField(Long sortField) {
    this.sortField = sortField;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

}
