package com.powerdialer.engine.dialer.endlistbehaviour;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Filter;
import com.powerdialer.util.Constants.CAMPAIGN_ACTION;
import com.powerdialer.util.Constants.FILTER_TYPE;
import com.powerdialer.util.Constants.LOG_MODULE;
import com.powerdialer.util.Utils;

/**
 * Accelerate all retries and callbacks on end of campaign data list
 */
public class StopAcceleratingBehaviour extends EndListBehaviourTemplate {

  private static long CHECK_PERIOD = 10000;

  StopAcceleratingBehaviour(CampaignContext campaignContext) {
    super(campaignContext);
  }

  @Override
  public void processEndList() {
    final Campaign campaign = campaignContext.getCampaign();
    Double now = Utils.convertDateToDouble(new Date());
    try {
      campaignContext.getDialerFacade().getCampaignDbOperations().setResultColumnToValue(campaign.getColResultRetryDate(),
          now,
          campaign,
          campaign.isApplyFilterRetries(),
          createFiltersForRetries(campaign));
      campaignContext.getDialerFacade().getCampaignDbOperations().setResultColumnToValue(campaign.getColResultCallbackDate(),
          now,
          campaign,
          campaign.isApplyFilterCallbacks(),
          createFiltersForCallbacks(campaign));
      cpsService.scheduleAtFixedRate(this::checkAndFinishCampaign, 0, CHECK_PERIOD, TimeUnit.MILLISECONDS);
    } catch (SQLException e) {
      LOG.error(e, e, LOG_MODULE.DB, campaignContext.getCampaign());
    }
  }

  /**
   * Create filter for calls which have no complete date and have retry date
   * @param campaign
   * @return
   */
  private List<Filter> createFiltersForRetries(Campaign campaign) {
    Filter retryDateFilter = new Filter();
    retryDateFilter.setType(FILTER_TYPE.FREE);
    retryDateFilter.setValue(" " + processColumnName(campaign, campaign.getColResultRetryDate()) + " IS NOT NULL ");

    Filter completeDateFilter = new Filter();
    completeDateFilter.setType(FILTER_TYPE.FREE);
    completeDateFilter.setValue(" " + processColumnName(campaign, campaign.getColResultCompleteDate()) + " IS NULL ");

    return Arrays.asList(retryDateFilter, completeDateFilter);
  }

  /**
   * Create filter for calls which have no complete date and have callback date
   * @param campaign
   * @return
   */
  private List<Filter> createFiltersForCallbacks(Campaign campaign) {
    Filter callbackDateFilter = new Filter();
    callbackDateFilter.setType(FILTER_TYPE.FREE);
    callbackDateFilter.setValue(" " + processColumnName(campaign, campaign.getColResultCallbackDate()) + " IS NOT NULL ");

    Filter completeDateFilter = new Filter();
    completeDateFilter.setType(FILTER_TYPE.FREE);
    completeDateFilter.setValue(" " + processColumnName(campaign, campaign.getColResultCompleteDate()) + " IS NULL ");

    return Arrays.asList(callbackDateFilter, completeDateFilter);
  }

  /**
   * If campaign do not have any calls in progress then shutdown it
   */
  private void checkAndFinishCampaign() {
    try {
      Campaign campaign = campaignContext.getCampaign();
      int callsInProducer = campaignContext.getCallProducer().getCallsCountInQueue();
      int callsInConsumer = campaignContext.getCallConsumer().getCallsCountInCallQueue();
      if (callsInProducer <= 0 && callsInConsumer <= 0 && !campaign.hasCallsInProgress()) {
        int notCompletedCallsCount = campaignContext.getDialerFacade().getCampaignDbOperations().getNotCompletedCallsCount(campaign);
        if (notCompletedCallsCount <= 0) {
          campaignContext.getDialerFacade().shutdownCampaign(campaign.getId(), CAMPAIGN_ACTION.STOP_REGULAR);
          cpsService.shutdown();
        }
      }
    } catch (SQLException e) {
      LOG.error(e, e, LOG_MODULE.DB, campaignContext.getCampaign());
    }
  }

}
