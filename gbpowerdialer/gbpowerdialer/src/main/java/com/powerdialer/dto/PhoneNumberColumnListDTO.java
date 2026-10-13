package com.powerdialer.dto;

import org.dozer.Mapping;

public class PhoneNumberColumnListDTO extends BaseEntityDTO {

  private String colNumber;
  private String colCallerId;
  private String colCallFromTime;
  private String colCallTillTime;
  private String colTimezoneOffset;
  private int colSort;
  private Long campaign;

  public String getColNumber() {
    return colNumber;
  }

  public void setColNumber(String colNumber) {
    this.colNumber = colNumber;
  }

  public String getColCallerId() {
    return colCallerId;
  }

  public void setColCallerId(String colCallerId) {
    this.colCallerId = colCallerId;
  }

  public String getColCallFromTime() {
    return colCallFromTime;
  }

  public void setColCallFromTime(String colCallFromTime) {
    this.colCallFromTime = colCallFromTime;
  }

  public String getColCallTillTime() {
    return colCallTillTime;
  }

  public void setColCallTillTime(String colCallTillTime) {
    this.colCallTillTime = colCallTillTime;
  }

  public String getColTimezoneOffset() {
    return colTimezoneOffset;
  }

  public void setColTimezoneOffset(String colTimezoneOffset) {
    this.colTimezoneOffset = colTimezoneOffset;
  }

  public int getColSort() {
    return colSort;
  }

  public void setColSort(int colSort) {
    this.colSort = colSort;
  }

  @Mapping("campaign.id")
  public Long getCampaign() {
    return campaign;
  }

  public void setCampaign(Long campaign) {
    this.campaign = campaign;
  }

}
