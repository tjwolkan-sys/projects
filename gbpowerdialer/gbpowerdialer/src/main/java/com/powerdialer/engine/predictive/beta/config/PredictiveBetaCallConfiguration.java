package com.powerdialer.engine.predictive.beta.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PredictiveBetaCallConfiguration {

  private static final Logger log = LoggerFactory.getLogger(PredictiveBetaCallConfiguration.class);

  private static final int RECALCULATION_INITIAL_DELAY_SEC = 60; // TODO: Decide on a better value, maybe more than 60 seconds ?
  private static final int RECALCULATION_INTERVAL_SEC = 10;
  private static final float DEFAULT_TOTAL_NUMBER_OF_CALLS_VALUE = 1000F; // Default value for total number of calls

  private PredictiveBetaCallConfigModel config = new PredictiveBetaCallConfigModel();

  public PredictiveBetaCallConfiguration() {
    PredictiveBetaCallConfigModel config = new PredictiveBetaCallConfigModel();
    config.setRecalculationInitialDelaySec(RECALCULATION_INITIAL_DELAY_SEC);
    config.setRecalculationIntervalSec(RECALCULATION_INTERVAL_SEC);
    config.setDefaultTotalNumberOfCallsValue(DEFAULT_TOTAL_NUMBER_OF_CALLS_VALUE);
    setConfig(config);
  }

  public PredictiveBetaCallConfigModel getConfig() {
    return config;
  }

  public void setConfig(PredictiveBetaCallConfigModel config) {
    this.config = config;
    log.info("Using new configuration: {}", config);
  }

}

