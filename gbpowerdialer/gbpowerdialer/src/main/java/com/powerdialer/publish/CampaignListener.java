package com.powerdialer.publish;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import com.powerdialer.exceptions.DateMismatchException;
import com.powerdialer.exceptions.StopConditionReachedException;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

@Component
public class CampaignListener implements ApplicationListener<CampaignChangedEvent> {

  private LoggerDecorator log;
  private CampaignService campaignService;

  @Autowired
  public CampaignListener(LoggerDecorator loggerDecorator, CampaignService campaignService) {
    this.log = loggerDecorator.getLogger(this.getClass());
    this.campaignService = campaignService;
  }

  @Override
  public void onApplicationEvent(CampaignChangedEvent event) {
    try {
      // FIX-333607: RESTART aksiyonu kilidi henüz bırakmamış olabilir; START uzun bekleme ile verilir.
      campaignService.startAfterRestart(event.getCampaignId());
    } catch (StopConditionReachedException | DateMismatchException e) {
      String reason = (e instanceof StopConditionReachedException) ? "stop condition" : "date mismatch";
      log.info("Campaign [id=" + event.getCampaignId() + "] could not be started due to " + reason, Constants.LOG_MODULE.CAMPAIGN);
      campaignService.cancelRestart(event.getCampaignId());
    }
  }

  public void setCampaignService(CampaignService campaignService) {
    this.campaignService = campaignService;
  }

}
