package com.powerdialer.dto;

import com.powerdialer.model.Campaign;
import org.dozer.Mapping;

import java.util.Date;

public class ScheduleBlackoutAutomationListDTO extends BaseEntityDTO {


  private Long id;
  private Long campaign;
  private Date startDate;
  private Date endDate;
  private String description;

  @Override
  public Long getId() {
    return id;
  }

  @Override
  public void setId(Long id) {
    this.id = id;
  }

  public Date getStartDate() {
    return startDate;
  }

  public void setStartDate(Date startDate) {
    this.startDate = startDate;
  }

  public Date getEndDate() {
    return endDate;
  }

  public void setEndDate(Date endDate) {
    this.endDate = endDate;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public void setDescription(String description) {
    this.description = description;
  }

  @Mapping("campaign.id")
  public Long getCampaign() {
    return campaign;
  }

  public void setCampaign(Long campaign) {
    this.campaign = campaign;
  }

}
