package com.powerdialer.dto;

import java.util.Date;

import org.dozer.Mapping;

public class ScheduleListDTO extends BaseEntityDTO {

  private int day;
  private Date from;
  private Date till;
  private Long campaign;
  private Boolean resetEnabled;

  public int getDay() {
    return day;
  }

  public void setDay(int day) {
    this.day = day;
  }

  public Date getFrom() {
    return from;
  }

  public void setFrom(Date from) {
    this.from = from;
  }

  public Date getTill() {
    return till;
  }

  public void setTill(Date till) {
    this.till = till;
  }

  public Boolean getResetEnabled() {
    return resetEnabled;
  }

  public void setResetEnabled(Boolean resetEnabled) {
    this.resetEnabled = resetEnabled;
  }

  @Mapping("campaign.id")
  public Long getCampaign() {
    return campaign;
  }

  public void setCampaign(Long campaign) {
    this.campaign = campaign;
  }

}
