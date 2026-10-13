package com.powerdialer.model;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Transient;

import com.powerdialer.util.Constants;

/**
 * Model for representing database column
 */
@Embeddable
public class Field {

  private Constants.DB_FIELD_TYPES fieldType;
  private String fieldName;

  @Enumerated(EnumType.STRING)
  @Column(name = "db_field_type")
  public Constants.DB_FIELD_TYPES getFieldType() {
    return fieldType;
  }

  public void setFieldType(Constants.DB_FIELD_TYPES fieldType) {
    this.fieldType = fieldType;
  }

  @Column(name = "field", columnDefinition = "NVARCHAR(255)")
  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String field) {
    this.fieldName = field;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    Field field1 = (Field) o;
    if (fieldType != field1.fieldType)
      return false;
    return Objects.equals(fieldName, field1.fieldName);

  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
    result = prime * result + ((fieldType == null) ? 0 : fieldType.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return fieldName;
  }

  @Transient
  public Constants.SIMPLE_DB_TYPES getSimpleDbType() {
    if (fieldType == null)
      return null;
    if (Constants.DECIMAL_FIELD_TYPES.contains(fieldType))
      return Constants.SIMPLE_DB_TYPES.DECIMAL;
    if (Constants.INTEGER_FIELD_TYPES.contains(fieldType))
      return Constants.SIMPLE_DB_TYPES.INTEGER;
    if (Constants.STRING_FIELD_TYPES.contains(fieldType))
      return Constants.SIMPLE_DB_TYPES.STRING;
    return Constants.SIMPLE_DB_TYPES.OTHER;
  }

}
