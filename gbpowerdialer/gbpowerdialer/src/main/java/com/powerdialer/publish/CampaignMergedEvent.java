package com.powerdialer.publish;

import org.springframework.context.ApplicationEvent;

import com.powerdialer.model.Campaign;

public class CampaignMergedEvent extends ApplicationEvent {

  private final Campaign oldCampaign, newCampaign;


  public CampaignMergedEvent(Object source, Campaign oldCampaign, Campaign newCampaign) {
    super(source);
    this.oldCampaign = oldCampaign;
    this.newCampaign = newCampaign;
  }

  public Campaign getOldCampaign() {
    return oldCampaign;
  }

  public Campaign getNewCampaign() {
    return newCampaign;
  }

}
