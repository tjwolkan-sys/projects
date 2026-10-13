package com.powerdialer.dto;

import java.util.Date;

public class DNCListDTO extends BaseEntityTenantDTO {

  private boolean global;
  private CampaignBasicDTO campaign;
  private Date expireDate;
  private boolean campaignDefault;

  public boolean isGlobal() { return global; }
  public void setGlobal(boolean global) {
    this.global = global;
  }

  public CampaignBasicDTO getCampaign() { return campaign; }
  public void setCampaign(CampaignBasicDTO campaign) { this.campaign = campaign; }

  public Date getExpireDate() {
    return expireDate;
  }
  public void setExpireDate(Date expireDate) {
    this.expireDate = expireDate;
  }

  public boolean isCampaignDefault() {
    return campaignDefault;
  }

  public void setCampaignDefault(boolean campaignDefault) {
    this.campaignDefault = campaignDefault;
  }
}
