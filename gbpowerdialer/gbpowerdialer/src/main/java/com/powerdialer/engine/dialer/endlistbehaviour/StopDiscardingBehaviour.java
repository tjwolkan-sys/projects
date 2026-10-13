package com.powerdialer.engine.dialer.endlistbehaviour;

import static com.powerdialer.util.Constants.DATA_FORMAT_WITH_DATE;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
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

/**
 * Discard all retries and callbacks on end of campaign data list
 */
public class StopDiscardingBehaviour extends EndListBehaviourTemplate {

  private static long CHECK_PERIOD = 10000;

  StopDiscardingBehaviour(CampaignContext campaignContext) {
    super(campaignContext);
  }

  @Override
  public void processEndList() {
    try {
      final Campaign campaign = campaignContext.getCampaign();
      String now = new SimpleDateFormat(DATA_FORMAT_WITH_DATE).format(new Date());
      campaignContext.getDialerFacade().getCampaignDbOperations().setResultColumnToValue(campaign.getColResultCompleteDate(), now, campaign, true, createFilters(campaign));
      cpsService.scheduleAtFixedRate(this::checkAndFinishCampaign, 0, CHECK_PERIOD, TimeUnit.MILLISECONDS);
    } catch (SQLException e) {
      LOG.error(e, e, LOG_MODULE.DB, campaignContext.getCampaign());
    }
  }

  /**
   * Create filters for data which need update
   */
  private List<Filter> createFilters(Campaign campaign) {
    Filter retryOrCallbackDateFilter = new Filter();
    retryOrCallbackDateFilter.setType(FILTER_TYPE.FREE);
    retryOrCallbackDateFilter
        .setValue(" " + processColumnName(campaign, campaign.getColResultCallbackDate()) + " IS NOT NULL OR " + processColumnName(campaign, campaign.getColResultRetryDate())
            + " IS NOT NULL ");

    Filter completeDateFilter = new Filter();
    completeDateFilter.setType(FILTER_TYPE.FREE);
    completeDateFilter.setValue(" " + processColumnName(campaign, campaign.getColResultCompleteDate()) + " IS NULL ");

    return Arrays.asList(retryOrCallbackDateFilter, completeDateFilter);
  }

  /**
   * If campaign do not have any calls in progress then shutdown it
   */
  private void checkAndFinishCampaign() {
    Campaign campaign = campaignContext.getCampaign();
    int callsInProducer = campaignContext.getCallProducer().getCallsCountInQueue();
    int callsInConsumer = campaignContext.getCallConsumer().getCallsCountInCallQueue();
    if (callsInProducer <= 0 && callsInConsumer <= 0 && !campaign.hasCallsInProgress()) {
      campaignContext.getDialerFacade().shutdownCampaign(campaign.getId(), CAMPAIGN_ACTION.STOP_REGULAR);
      cpsService.shutdown();
    }
  }

}
