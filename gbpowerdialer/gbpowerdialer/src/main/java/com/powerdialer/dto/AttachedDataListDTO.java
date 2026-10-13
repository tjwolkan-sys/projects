package com.powerdialer.dto;

public class AttachedDataListDTO extends BaseEntityDTO {

  private Long id;
  private String field;
  private String fieldName;
  private Boolean popup;
  private String popupName;
  private Boolean saveLongTerm;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  public Boolean getPopup() {
    return popup;
  }

  public void setPopup(Boolean popup) {
    this.popup = popup;
  }

  public String getPopupName() {
    return popupName;
  }

  public void setPopupName(String popupName) {
    this.popupName = popupName;
  }

  public Boolean getSaveLongTerm() {
    return saveLongTerm;
  }

  public void setSaveLongTerm(Boolean saveLongTerm) {
    this.saveLongTerm = saveLongTerm;
  }
}
