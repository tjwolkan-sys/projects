package com.powerdialer.engine.dialer.state;

import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.LoggerDecorator;

/**
 * State for campaign, instance of this class is used when campaign is in pause
 * state
 */
public class PauseState extends CampaignStateTemplate {

  private final LoggerDecorator LOG;

  public PauseState(CampaignContext campaignContext) {
    super(campaignContext);
    this.LOG = campaignContext.getDialerFacade().getLoggerDecorator().getLogger(PauseState.class);
  }

  @Override
  public void start() {
    addCallQueueToAsteriskCallQueues();
    super.start();
  }

  @Override
  public void pause() {
    LOG.info(campaignContext.getCampaign() + " is already paused!", LOG_MODULE.CAMPAIGN, campaignContext.getCampaign(), getAuthenticatedUser());
  }

}
