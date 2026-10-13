package com.powerdialer.dto;


import java.util.List;

public class CampaignPriorityListDTO extends BaseEntityDTO {

  private Long id;
  private String campaignName;
  private List<CampaignPriorityFilterFormDTO> campaignPriorityFilters;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCampaignName() {
    return campaignName;
  }

  public void setCampaignName(String campaignName) {
    this.campaignName = campaignName;
  }

  public List<CampaignPriorityFilterFormDTO> getCampaignPriorityFilters() {
    return campaignPriorityFilters;
  }

  public void setCampaignPriorityFilters(List<CampaignPriorityFilterFormDTO> campaignPriorityFilters) {
    this.campaignPriorityFilters = campaignPriorityFilters;
  }
}
