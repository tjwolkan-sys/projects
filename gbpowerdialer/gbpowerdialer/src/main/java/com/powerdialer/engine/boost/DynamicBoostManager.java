package com.powerdialer.engine.boost;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.powerdialer.engine.predictive.dispatcher.PredictiveAlphaEventDispatcher;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.publish.CampaignMergedEvent;
import com.powerdialer.redis.event.predictive.PredictiveEventFactory;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
public class DynamicBoostManager {

  private static final Logger log = LoggerFactory.getLogger(DynamicBoostManager.class);

  private final Map<Long, CampaignBoostData> campaignIdToBoostData = new ConcurrentHashMap<>();
  private ScheduledExecutorService scheduledExecutorService;

  @Autowired
  private DynamicBoostCalculator dynamicBoostCalculator;

  @Autowired
  private DynamicBoostTestConfigurator dynamicBoostTestConfigurator;

  @Autowired
  private PredictiveAlphaEventDispatcher predictiveAlphaEventDispatcher;

  @Autowired
  private PredictiveEventFactory predictiveEventFactory;

  @PostConstruct
  private void init() {
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
        .setNameFormat("boost-manager-executor-%d")
        .build();
    this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
  }

  // TODO remove
  static Call createFakeCall(Long campaignId) {
    Call call = new Call();
    Campaign campaign = new Campaign();
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setId(campaignId);
    call.setCampaign(campaign);
    call.setStartDate(Utils.convertDateToDouble(new Date()));
    return call;
  }

  // TODO remove
  public CampaignBoostData getTestData(Long campaignId) {
    return campaignIdToBoostData.get(campaignId);
  }

  public float getBoostValue(Long campaignId) {
    return Optional.ofNullable(campaignIdToBoostData.get(campaignId))
            .map(CampaignBoostData::getCurrentData)
            .map(PeriodicBoostData::getBoostValue)
            .orElse(dynamicBoostTestConfigurator.getConfig().getDefaultBoostValue());
  }

  public void onCallFailed(Call call) {
    if (checkCampaign(call.getCampaign())) {
      long diffSeconds = (System.currentTimeMillis() - Utils.convertDoubleToLongDate(call.getStartDate())) / 1000;
      if (diffSeconds < dynamicBoostTestConfigurator.getConfig().getFailedCallMaxConsiderationTimeSec()) {
        getDataAndExecuteSynchronously(call.getCampaign().getId(), boostData -> {
          log.debug("Incrementing failed calls for {}", boostData.getCampaign());
          boostData.getCurrentData().getFailedCallsCount().incrementAndGet();
        });
      } else {
        log.info("Ignoring failed call that was started {} sec ago: {}", diffSeconds, call);
      }
    }
  }

  public void onCallAnsweredByClient(Call call) {
    if (checkCampaign(call.getCampaign())) {
      getDataAndExecuteSynchronously(call.getCampaign().getId(), boostData -> {
        log.debug("Incrementing answered calls for {}", boostData.getCampaign());
        boostData.getCurrentData().getAnsweredCallsCount().incrementAndGet();
      });
    }
  }

  public void onCallAbandoned(Call call) {
    if (checkCampaign(call.getCampaign())) {
      getDataAndExecuteSynchronously(call.getCampaign().getId(), boostData -> {
        log.debug("Incrementing abandoned calls for {}", boostData.getCampaign());
        int abandonCallsCount = boostData.getCurrentData().getAbandonedCallsCount().incrementAndGet();
        if (abandonCallsCount >= dynamicBoostTestConfigurator.getConfig().getEmergencyRecalculationAbandonedCallsThreshold()) {
          log.info("Emergency recalculation due to abandon calls threshold for {}", boostData.getCampaign());
          float newBoostValue = dynamicBoostCalculator.calculateBoost(boostData);
          restartCalculationSync(boostData, newBoostValue, false);
        }
      });
    }
  }

  public void onCallAnsweredByAgent(Call call) {
    if (checkCampaign(call.getCampaign())) {
      getDataAndExecuteSynchronously(call.getCampaign().getId(), boostData -> {
        log.debug("Incrementing handled calls for {}", boostData.getCampaign());
        boostData.getCurrentData().getHandledCallsCount().incrementAndGet();
      });
    }
  }

  public void onCampaignStart(Campaign campaign) {
    restartCalculation(campaign, false);
  }

  public void onCampaignStop(Campaign campaign) {
    if (checkCampaign(campaign)) {
      getDataAndExecuteSynchronously(campaign.getId(), boostData -> {
        log.info("Stopping dynamic boost calculation for campaign {}", campaign);
        stopRecalculationFuture(boostData);
      });
    }
  }

  @EventListener(CampaignMergedEvent.class)
  public void onCampaignMerged(CampaignMergedEvent event) {
    Campaign campaign = event.getNewCampaign();
    if (checkCampaign(campaign)) {
      getDataAndExecuteSynchronously(campaign.getId(), boostData -> {
        log.info("Updating {}", campaign);
        boostData.setCampaign(campaign);
      });
    }
  }

  public void resetCalculation(Campaign campaign) {
    restartCalculation(campaign, true);
  }

  private void restartCalculation(Campaign campaign, boolean forceDefaultBoostValue) {
    if (checkCampaign(campaign)) {
      getOrCreateDataAndExecuteSynchronously(campaign, boostData -> {
        float boostValue = (forceDefaultBoostValue)
            ? dynamicBoostTestConfigurator.getConfig().getDefaultBoostValue() :
            getInitialBoostValue(boostData);
        log.info("Restarting dynamic boost calculation for campaign {} with boost value {}", boostData.getCampaign(), boostValue);
        restartCalculationSync(boostData, boostValue, true);
      });
    }
  }

  private void restartCalculationSync(CampaignBoostData boostData, float boostValue, boolean useInitialDelay) {
    int initialDelay = (useInitialDelay)
        ? dynamicBoostTestConfigurator.getConfig().getRecalculationInitialDelaySec()
        : dynamicBoostTestConfigurator.getConfig().getRecalculationIntervalSec();
    stopRecalculationFuture(boostData);
    boostData.restartCalculation(boostValue);
    predictiveAlphaEventDispatcher.dispatchAsync(
        predictiveEventFactory.createAlpha(boostData)
    );
    boostData.setRecalculationFuture(scheduledExecutorService.scheduleAtFixedRate(() -> recalculateBoost(boostData.getCampaign()),
        initialDelay, dynamicBoostTestConfigurator.getConfig().getRecalculationIntervalSec(), TimeUnit.SECONDS));
  }

  private void recalculateBoost(Campaign campaign) {
    try {
      getDataAndExecuteSynchronously(campaign.getId(), boostData -> {
        int callsMadeWithinInterval = boostData.getCurrentData().getFailedCallsCount().get()
            + boostData.getCurrentData().getAnsweredCallsCount().get();
        if (callsMadeWithinInterval < dynamicBoostTestConfigurator.getConfig().getRecalculationIntervalMinCallsCount()) {
          log.debug("Not enough failed + answered calls ({} of {} needed) for recalculation, prolonging interval: {}",
                  callsMadeWithinInterval, dynamicBoostTestConfigurator.getConfig().getRecalculationIntervalMinCallsCount(), campaign);
        } else {
          log.debug("Initiating boost recalculation for campaign {}", campaign);
          float newBoostValue = dynamicBoostCalculator.calculateBoost(boostData);
          boostData.restartCalculation(newBoostValue);
        }
      });
    } catch (Exception e) {
      log.warn("Could not recalculate boost for {}. Continuing.", campaign, e);
    }
  }

  private void getDataAndExecuteSynchronously(Long campaignId, Consumer<CampaignBoostData> consumer) {
    CampaignBoostData boostData = campaignIdToBoostData.get(campaignId);
    if (boostData != null) {
      executeSynchronously(consumer, boostData);
    } else {
      log.warn("Could not obtain campaign boost data for campaign id {}", campaignId);
    }
  }
/*
  public PredictivePublisher getPredictivePublisher() {
    return predictivePublisher;
  }

  @Async
  public void publishPredictiveEvent(CampaignBoostData boostData) {
    try {
      log.info("Publishing test data for campaign {}", boostData.getCampaign());
      getPredictivePublisher().publishPredictiveAlphaEvent(boostData);
    } catch (Exception e) {
      log.error("Error while publishing test data for campaign {}", boostData.getCampaign(), e);
    }
  }
*/


  private void getOrCreateDataAndExecuteSynchronously(Campaign campaign, Consumer<CampaignBoostData> consumer) {
    CampaignBoostData boostData = campaignIdToBoostData.computeIfAbsent(campaign.getId(), c -> new CampaignBoostData(campaign));
    executeSynchronously(consumer, boostData);
  }

  private void executeSynchronously(Consumer<CampaignBoostData> consumer, CampaignBoostData boostData) {
    boostData.lock();
    try {
      consumer.accept(boostData);
    } finally {
      boostData.unlock();
    }
  }

  private void stopRecalculationFuture(CampaignBoostData boostData) {
    ScheduledFuture<?> recalculationFuture = boostData.getRecalculationFuture();
    if (recalculationFuture != null) {
      log.info("Stopping recalculation future for campaign {}...", boostData.getCampaign());
      recalculationFuture.cancel(true);
      log.info("Recalculation future cancelled for campaign {}", boostData.getCampaign());
      boostData.setRecalculationFuture(null);
    }
  }

  private boolean checkCampaign(Campaign campaign) {
    return campaign != null && !campaign.isDeleted() && campaign.getType() == Constants.CAMPAIGN_TYPE.PREDICTIVE;
  }

  private float getInitialBoostValue(CampaignBoostData boostData) {
    if (boostData.getCurrentData() != null) {
      long diffSeconds = (System.currentTimeMillis() - boostData.getCurrentData().getCalculationIntervalStartTime().getTime()) / 1000;
      if (diffSeconds < dynamicBoostTestConfigurator.getConfig().getStoppedCampaignBoostPreservationTimeSec()) {
        return boostData.getCurrentData().getBoostValue();
      }
    }
    return dynamicBoostTestConfigurator.getConfig().getDefaultBoostValue();
  }

}
