package com.powerdialer.dto;


import com.powerdialer.util.Constants;

import java.util.List;

public class CampaignPriorityFilterListDTO extends BaseEntityDTO {

  private Long id;
  private String field;
  private String type;
  private String targetTable;
  private Constants.FILTER_CONDITION condition;
  private String value;
  private Boolean technical;
  private String db_field_type;
  private Constants.FILTER_JOIN_TYPE logicOperator;

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

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getTargetTable() {
    return targetTable;
  }

  public void setTargetTable(String targetTable) {
    this.targetTable = targetTable;
  }

  public Constants.FILTER_CONDITION getCondition() {
    return condition;
  }

  public void setCondition(Constants.FILTER_CONDITION condition) {
    this.condition = condition;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public Boolean getTechnical() {
    return technical;
  }

  public void setTechnical(Boolean technical) {
    this.technical = technical;
  }

  public String getDb_field_type() {
    return db_field_type;
  }

  public void setDb_field_type(String db_field_type) {
    this.db_field_type = db_field_type;
  }

  public Constants.FILTER_JOIN_TYPE getLogicOperator() {
    return logicOperator;
  }

  public void setLogicOperator(Constants.FILTER_JOIN_TYPE logicOperator) {
    this.logicOperator = logicOperator;
  }
}
