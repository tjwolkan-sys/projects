package com.powerdialer.dto;

import org.dozer.Mapping;

public class AdminLogDetailsListDTO extends BaseEntityDTO{

  private Long adminLog;
  private String entityName;
  private String field;
  private String previousValue;
  private String newValue;
  private String added;
  private String removed;

  @Mapping("adminLog.id")
  public Long getAdminLog() {
    return adminLog;
  }

  public void setAdminLog(Long adminLog) {
    this.adminLog = adminLog;
  }

  public String getEntityName() {
    return entityName;
  }

  public void setEntityName(String entityName) {
    this.entityName = entityName;
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public String getPreviousValue() {
    return previousValue;
  }

  public void setPreviousValue(String previousValue) {
    this.previousValue = previousValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public void setNewValue(String newValue) {
    this.newValue = newValue;
  }

  public String getAdded() {
    return added;
  }

  public void setAdded(String added) {
    this.added = added;
  }

  public String getRemoved() {
    return removed;
  }

  public void setRemoved(String removed) {
    this.removed = removed;
  }

}
