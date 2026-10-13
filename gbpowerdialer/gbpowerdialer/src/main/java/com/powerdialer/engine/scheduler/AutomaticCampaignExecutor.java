package com.powerdialer.engine.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.Constants.CAMPAIGN_STATE;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.LoggerDecorator;

/**
 * Class that starts or stops campaigns based on data from
 * CampaignScheduleChecker
 */
@Component
public class AutomaticCampaignExecutor {

  @Autowired
  private CampaignService campaignService;

  @Autowired
  private CampaignScheduleChecker checker;

  private final LoggerDecorator LOG;

  @Autowired
  public AutomaticCampaignExecutor(LoggerDecorator loggerDecorator) {
    this.LOG = loggerDecorator.getLogger(AutomaticCampaignExecutor.class);
  }

  public void iterateCampaigns() {
    try {
      LOG.info("Performing schedule check...");
      checker.setNow();
      campaignService.getCampaignsWithSchedules().forEach(this::checkCampaign);
      checker.setFirstCheckToFalse();
    } catch (Exception e) {
      LOG.error("Could not perform schedule check", e);
    }
  }

  public void checkCampaign(Campaign campaign) {
    try {
      checker.checkCampaign(campaign);
      if (campaign.getScheduleAutomationEnabled() && checker.isMustBeStarted())
        checker.checkCampaignAutomation(campaign);
      executeCampaign(campaign);
    } catch (Exception e) {
      LOG.error("Could not perform schedule check for " + campaign, e, LOG_MODULE.CAMPAIGN);
    }
  }

  public void executeCampaign(Campaign campaign) {
    stopCampaign(campaign);
    startCampaign(campaign);
  }

  private void startCampaign(Campaign campaign) {
    CAMPAIGN_STATE campaignState = getCampaignState(campaign);
    if (!checker
        .isMustBeStarted() || campaignState == CAMPAIGN_STATE.STOPPING || campaignState == CAMPAIGN_STATE.STARTING || campaignState == CAMPAIGN_STATE.RUNNING
        || campaignState == CAMPAIGN_STATE.PAUSING || campaignState == CAMPAIGN_STATE.PAUSED || campaignState == CAMPAIGN_STATE.RELOADING || campaignState == CAMPAIGN_STATE.RESETTING)
      return;
    LOG.info(campaign + " is going to be started automatically!", LOG_MODULE.CAMPAIGN, campaign);

    if (checker.isResetEnabled())
        campaignService.actionAsync(campaign.getId(), CAMPAIGN_ACTION.RESET, null);

    campaignService.actionAsync(campaign.getId(), CAMPAIGN_ACTION.START, null);
  }

  private void stopCampaign(Campaign campaign) {
    CAMPAIGN_STATE campaignState = getCampaignState(campaign);
    if (!checker
        .isMustBeStopped() || campaignState == CAMPAIGN_STATE.STOPPED || campaignState == CAMPAIGN_STATE.STOPPING || campaignState == CAMPAIGN_STATE.STARTING
        || campaignState == CAMPAIGN_STATE.PAUSING || campaignState == CAMPAIGN_STATE.PAUSED || campaignState == CAMPAIGN_STATE.RELOADING || campaignState == CAMPAIGN_STATE.RESETTING)
      return;
    LOG.info(campaign + " is going to be stopped automatically!", LOG_MODULE.CAMPAIGN, campaign);
    campaignService.actionAsync(campaign.getId(), CAMPAIGN_ACTION.STOP_REGULAR, Constants.CAMPAIGN_STOP_TYPE.AUTOMATIC);
  }

  private CAMPAIGN_STATE getCampaignState(Campaign campaign) {
    CampaignContext campaignContext = campaignService.getDialerFacade().getCampaignContexts().get(campaign.getId());
    return campaignContext == null
        ? CAMPAIGN_STATE.STOPPED
        : campaignContext.getCampaign().getState();
  }

}
