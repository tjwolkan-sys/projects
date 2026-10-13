package com.powerdialer.engine.boost;

import com.powerdialer.model.Campaign;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DynamicBoostCalculator {

  private static final Logger log = LoggerFactory.getLogger(DynamicBoostCalculator.class);

  @Autowired
  private DynamicBoostTestConfigurator dynamicBoostTestConfigurator;

  public float calculateBoost(CampaignBoostData boostData) {
    Campaign campaign = boostData.getCampaign();
    log.debug("Starting boost recalculation for {} and {}", campaign, boostData);
    float currentBoostValue = boostData.getCurrentData().getBoostValue();
    float currentAbandonRate = getAbandonRate(boostData.getCurrentData());
    float abandonRateLimit = campaign.getMaxAbandonRate();
    float currentAnswerRate = getAnswerRate(boostData.getCurrentData());
    float answerRateLimit = campaign.getMaxAnswerRate();
    float result;
    log.debug("Answer rate (current: {}, limit: {}), abandon rate (current: {}, limit: {}) for {}",
        currentAnswerRate, answerRateLimit, currentAbandonRate, abandonRateLimit, campaign);

    if (currentAbandonRate > abandonRateLimit) {
      result = getResultBoostRegardingAbandonRate(currentBoostValue, currentAbandonRate, abandonRateLimit);

    } else if (currentAnswerRate == answerRateLimit) {
      result = currentBoostValue;

    } else {
      result = getResultBoostRegardingAnswerRate(currentBoostValue, currentAnswerRate, answerRateLimit);
    }
    result = boundResultValue(result);
    log.info("Current boost value: {}, new value: {}, delta: {} for campaign {}",
        currentBoostValue, result, result - currentBoostValue, campaign);
    return result;
  }

  private float getResultBoostRegardingAnswerRate(float currentBoostValue, float answerRate, float answerRateLimit) {
    float targetBoostValue;
    if (answerRate == 0) {
      log.debug("Answer rate is zero, using constant multiplier");
      targetBoostValue = currentBoostValue * dynamicBoostTestConfigurator.getConfig().getZeroAnswerRateTargetBoostMultiplier();
    } else {
      targetBoostValue = answerRateLimit / answerRate;
    }
    log.debug("Target boost value regarding answer rate: {}", targetBoostValue);
    float deltaFactor = (targetBoostValue > currentBoostValue)
        ? dynamicBoostTestConfigurator.getConfig().getAnswerRateBoostIncreaseDeltaFactor()
        : dynamicBoostTestConfigurator.getConfig().getAnswerRateBoostDecreaseDeltaFactor();
    return getResultBoost(currentBoostValue, targetBoostValue, deltaFactor);
  }

  private float getResultBoostRegardingAbandonRate(float currentBoostValue, float abandonRate, float abandonRateLimit) {
    float targetBoostValue = currentBoostValue - currentBoostValue * (abandonRate - abandonRateLimit);
    log.debug("Target boost value regarding abandon rate: {}", targetBoostValue);
    float deltaFactor = dynamicBoostTestConfigurator.getConfig().getAbandonRateBoostDecreaseDeltaFactor();
    return getResultBoost(currentBoostValue, targetBoostValue, deltaFactor);
  }

  private float getAnswerRate(PeriodicBoostData periodicBoostData) {
    int answeredCallsCount = periodicBoostData.getAnsweredCallsCount().get();
    int failedCallsCount = periodicBoostData.getFailedCallsCount().get();
    if (answeredCallsCount + failedCallsCount == 0) {
      throw new IllegalStateException("Can not perform calculation on zero value of failed and answered calls");
    }
    return (float) answeredCallsCount / (answeredCallsCount + failedCallsCount);
  }

  private float getAbandonRate(PeriodicBoostData periodicBoostData) {
    int abandonedCallsCount = periodicBoostData.getAbandonedCallsCount().get();
    int handledCallsCount = periodicBoostData.getHandledCallsCount().get();
    if (abandonedCallsCount + handledCallsCount == 0) {
      return 0;
    }
    return (float) abandonedCallsCount / (abandonedCallsCount + handledCallsCount);
  }

  private float getResultBoost(float currentBoost, float targetBoost, float deltaFactor) {
    float boostDelta = targetBoost - currentBoost;
    return currentBoost + boostDelta * deltaFactor;
  }

  private float boundResultValue(float value) {
    float lowerBound = dynamicBoostTestConfigurator.getConfig().getMinBoostValue();
    float upperBound = dynamicBoostTestConfigurator.getConfig().getMaxBoostValue();
    if (value < lowerBound) {
      log.debug("Bounding result boost value {} to lower limit {}", value, lowerBound);
      return lowerBound;
    } else if (value > upperBound) {
      log.debug("Bounding result boost value {} to upper limit {}", value, upperBound);
      return upperBound;
    }
    return value;
  }

}
