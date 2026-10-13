package com.powerdialer.engine;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

@Component
public class StopConditionChecker {

  private LoggerDecorator log;

  @Autowired
  private LoggerDecorator loggerDecorator;

  @PostConstruct
  private void init() {
    log = loggerDecorator.getLogger(this.getClass());
  }

  public boolean isStopConditionReached(Campaign campaign) {
    if (!Boolean.TRUE.equals(campaign.getStopConditionEnabled()))
      return false;
    if (campaign.getStopConditionParameter() == null || campaign.getStopConditionThreshold() == null
        || (campaign.getStopConditionValue() == null
            && (campaign.getStopConditionParameter() == Constants.STOP_CONDITION_PARAMETER.TELEPHONY_RC_COUNT
                || campaign.getStopConditionParameter() == Constants.STOP_CONDITION_PARAMETER.USER_RC_COUNT))) {
      log.error("Campaign " + campaign.getId() + " has incomplete stop condition parameters");
      return false;
    }
    if (campaign.getStopConditionParameter() == Constants.STOP_CONDITION_PARAMETER.USER_RC_COUNT) {
      Long callsCount = campaign.getCallsByUserResultCode().get(campaign.getStopConditionValue());
      return (callsCount != null && callsCount >= campaign.getStopConditionThreshold());
    } else if (campaign.getStopConditionParameter() == Constants.STOP_CONDITION_PARAMETER.TELEPHONY_RC_COUNT) {
      Long callsCount = campaign.getCallsByTelephonyResultCode().get(campaign.getStopConditionValue());
      return (callsCount != null && callsCount >= campaign.getStopConditionThreshold());
    } else if (campaign.getStopConditionParameter() == Constants.STOP_CONDITION_PARAMETER.TOUCHED_CONTACTS_COUNT) {
      return (campaign.getTouchedContacts() >= campaign.getStopConditionThreshold());
    } else if (campaign.getStopConditionParameter() == Constants.STOP_CONDITION_PARAMETER.COMPLETED_CONTACTS_COUNT) {
      return (campaign.getCompletedContacts() >= campaign.getStopConditionThreshold());
    }
    return false;
  }

}
