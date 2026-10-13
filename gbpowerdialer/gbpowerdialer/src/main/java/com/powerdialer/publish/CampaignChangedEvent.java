package com.powerdialer.publish;

import org.springframework.context.ApplicationEvent;

public class CampaignChangedEvent extends ApplicationEvent {

  private static final long serialVersionUID = 4902159534334365578L;
  private Long campaignId;

  public CampaignChangedEvent(Object source, Long campaignId) {
    super(source);
    this.campaignId = campaignId;
  }

  public Long getCampaignId() {
    return campaignId;
  }

}
