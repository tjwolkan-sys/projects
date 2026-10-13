package com.powerdialer.dto;

public class FilterTestDTO extends FilterFormDTO {

  private CampaignFormDTO campaign;

  public CampaignFormDTO getCampaign() { return campaign; }

  public void setCampaign(CampaignFormDTO campaign) { this.campaign = campaign; }
}
