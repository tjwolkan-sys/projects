package com.powerdialer.dto;


public class FilterListDTO extends BaseEntityDTO {

  private Long id;
  private FieldDTO field;
  private int type;
  private Integer targetTable;
  private Integer condition;
  private String value;
  private Boolean technical;
  private Boolean segmentation;
  private Integer ratio;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public FieldDTO getField() {
    return field;
  }

  public void setField(FieldDTO field) {
    this.field = field;
  }

  public int getType() {
    return type;
  }

  public void setType(int type) {
    this.type = type;
  }

  public Integer getTargetTable() {
    return targetTable;
  }

  public void setTargetTable(Integer targetTable) {
    this.targetTable = targetTable;
  }

  public Integer getCondition() {
    return condition;
  }

  public void setCondition(Integer condition) {
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

  public Boolean getSegmentation() {
    return segmentation;
  }

  public void setSegmentation(Boolean segmentation) {
    this.segmentation = segmentation;
  }

  public Integer getRatio() {
    return ratio;
  }

  public void setRatio(Integer ratio) {
    this.ratio = ratio;
  }
}
