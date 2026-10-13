package com.powerdialer.engine.dialer.state;

import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.LoggerDecorator;

/**
 * State for campaign, instance of this class is used when campaign is in start
 * state
 */
public class StartState extends CampaignStateTemplate {

  private final LoggerDecorator LOG;

  public StartState(CampaignContext campaignContext) {
    super(campaignContext);
    this.LOG = campaignContext.getDialerFacade().getLoggerDecorator().getLogger(StartState.class);
  }

  @Override
  public void start() {
    LOG.info(campaignContext.getCampaign() + " has already started!", LOG_MODULE.CAMPAIGN, campaignContext.getCampaign(), getAuthenticatedUser());
  }

}
