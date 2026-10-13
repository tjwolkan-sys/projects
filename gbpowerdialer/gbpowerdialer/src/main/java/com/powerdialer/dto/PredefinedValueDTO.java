package com.powerdialer.dto;

import com.powerdialer.util.Constants;


public class PredefinedValueDTO extends BaseEntityDTO {

  private double value;
  private Constants.INPUT_VALUE_TYPE type;

  public double getValue() {
    return value;
  }

  public void setValue(double value) {
    this.value = value;
  }

  public Constants.INPUT_VALUE_TYPE getType() {
    return type;
  }

  public void setType(Constants.INPUT_VALUE_TYPE type) {
    this.type = type;
  }
}
