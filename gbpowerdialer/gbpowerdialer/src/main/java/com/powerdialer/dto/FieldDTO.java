package com.powerdialer.dto;

import com.powerdialer.util.Constants;

public class FieldDTO {

  private Constants.DB_FIELD_TYPES fieldType;
  private String fieldName;

  public FieldDTO() {}

  public FieldDTO(String fieldName) {
    this.fieldName = fieldName;
  }

  public FieldDTO(Constants.DB_FIELD_TYPES fieldType,
                  String fieldName) {
    this.fieldType = fieldType;
    this.fieldName = fieldName;
  }

  public Constants.DB_FIELD_TYPES getFieldType() {
    return fieldType;
  }

  public void setFieldType(Constants.DB_FIELD_TYPES fieldType) {
    this.fieldType = fieldType;
  }

  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  public Constants.SIMPLE_DB_TYPES getSimpleDbType() {
    if (fieldType == null)
      return null;
    if (Constants.DECIMAL_FIELD_TYPES.contains(fieldType))
      return Constants.SIMPLE_DB_TYPES.DECIMAL;
    if (Constants.INTEGER_FIELD_TYPES.contains(fieldType))
      return Constants.SIMPLE_DB_TYPES.INTEGER;
    if (Constants.STRING_FIELD_TYPES.contains(fieldType))
      return Constants.SIMPLE_DB_TYPES.STRING;
    if (Constants.DATE_FIELD_TYPES.contains(fieldType))
      return Constants.SIMPLE_DB_TYPES.DATE;
    return Constants.SIMPLE_DB_TYPES.OTHER;
  }

}
