package com.powerdialer.publish;

import org.springframework.context.ApplicationEvent;

import com.powerdialer.model.Campaign;

public class CampaignCreatedEvent extends ApplicationEvent {

  private final Campaign campaign;


  public CampaignCreatedEvent(Object source, Campaign campaign) {
    super(source);
    this.campaign = campaign;
  }

  public Campaign getCampaign() {
    return campaign;
  }

}
