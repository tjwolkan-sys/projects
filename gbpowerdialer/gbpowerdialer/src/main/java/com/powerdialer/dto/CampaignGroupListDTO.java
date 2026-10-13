package com.powerdialer.dto;

import org.dozer.Mapping;

public class CampaignGroupListDTO extends BaseEntityDTO {

  private Long campaign;
  private GroupFormDTO group;
  private int priority;

  @Mapping("campaign.id")
  public Long getCampaign() {
    return campaign;
  }

  public void setCampaign(Long campaign) {
    this.campaign = campaign;
  }

  public int getPriority() {
    return priority;
  }

  public GroupFormDTO getGroup() {
    return group;
  }

  public void setGroup(GroupFormDTO group) {
    this.group = group;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

}
