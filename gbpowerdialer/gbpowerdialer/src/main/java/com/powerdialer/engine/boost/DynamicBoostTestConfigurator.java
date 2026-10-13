package com.powerdialer.engine.boost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DynamicBoostTestConfigurator {

  private static final Logger log = LoggerFactory.getLogger(DynamicBoostTestConfigurator.class);
  private static final float DEFAULT_BOOST_VALUE = 1;
  private static final float MIN_BOOST_VALUE = 1;
  private static final float MAX_BOOST_VALUE = 6;

  private static final int FAILED_CALL_MAX_CONSIDERATION_TIME_SEC = 3600;
  private static final int RECALCULATION_INTERVAL_MIN_CALLS_COUNT = 10;
  private static final int RECALCULATION_INITIAL_DELAY_SEC = 60;
  private static final int RECALCULATION_INTERVAL_SEC = 30;
  private static final int STOPPED_CAMPAIGN_BOOST_PRESERVATION_TIME_SEC = 60;
  private static final int EMERGENCY_RECALCULATION_ABANDONED_CALLS_THRESHOLD = 2;

  private static final float ABANDON_RATE_BOOST_DECREASE_DELTA_FACTOR = 0.75F;
  private static final float ANSWER_RATE_BOOST_DECREASE_DELTA_FACTOR = 0.33F;
  private static final float ANSWER_RATE_BOOST_INCREASE_DELTA_FACTOR = 0.2F;
  private static final float ZERO_ANSWER_RATE_BOOST_MULTIPLIER = 1.5F;

  private DynamicBoostTestConfig config = new DynamicBoostTestConfig();

  public DynamicBoostTestConfigurator() {
    DynamicBoostTestConfig config = new DynamicBoostTestConfig();
    config.setDefaultBoostValue(DEFAULT_BOOST_VALUE);
    config.setMinBoostValue(MIN_BOOST_VALUE);
    config.setMaxBoostValue(MAX_BOOST_VALUE);

    config.setRecalculationInitialDelaySec(RECALCULATION_INITIAL_DELAY_SEC);
    config.setRecalculationIntervalSec(RECALCULATION_INTERVAL_SEC);
    config.setFailedCallMaxConsiderationTimeSec(FAILED_CALL_MAX_CONSIDERATION_TIME_SEC);
    config.setStoppedCampaignBoostPreservationTimeSec(STOPPED_CAMPAIGN_BOOST_PRESERVATION_TIME_SEC);
    config.setRecalculationIntervalMinCallsCount(RECALCULATION_INTERVAL_MIN_CALLS_COUNT);
    config.setEmergencyRecalculationAbandonedCallsThreshold(EMERGENCY_RECALCULATION_ABANDONED_CALLS_THRESHOLD);

    config.setAbandonRateBoostDecreaseDeltaFactor(ABANDON_RATE_BOOST_DECREASE_DELTA_FACTOR);
    config.setAnswerRateBoostDecreaseDeltaFactor(ANSWER_RATE_BOOST_DECREASE_DELTA_FACTOR);
    config.setAnswerRateBoostIncreaseDeltaFactor(ANSWER_RATE_BOOST_INCREASE_DELTA_FACTOR);
    config.setZeroAnswerRateTargetBoostMultiplier(ZERO_ANSWER_RATE_BOOST_MULTIPLIER);
    config.setPublishOnRecalculation(false);
    setConfig(config);
  }

  public DynamicBoostTestConfig getConfig() {
    return config;
  }

  public void setConfig(DynamicBoostTestConfig config) {
    this.config = config;
    log.info("Using new configuration: {}", config);
  }

}
