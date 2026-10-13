package com.powerdialer.engine.boost;

public class DynamicBoostTestConfig {

  private volatile float defaultBoostValue;
  private volatile float minBoostValue;
  private volatile float maxBoostValue;

  private volatile int recalculationInitialDelaySec;
  private volatile int recalculationIntervalSec;
  private volatile int failedCallMaxConsiderationTimeSec;
  private volatile int stoppedCampaignBoostPreservationTimeSec;
  private volatile int recalculationIntervalMinCallsCount;
  private volatile int emergencyRecalculationAbandonedCallsThreshold;

  private volatile float abandonRateBoostDecreaseDeltaFactor;
  private volatile float answerRateBoostDecreaseDeltaFactor;
  private volatile float answerRateBoostIncreaseDeltaFactor;
  private volatile float zeroAnswerRateTargetBoostMultiplier;

  public float getDefaultBoostValue() {
    return defaultBoostValue;
  }

  public void setDefaultBoostValue(float defaultBoostValue) {
    this.defaultBoostValue = defaultBoostValue;
  }

  public float getMinBoostValue() {
    return minBoostValue;
  }

  public void setMinBoostValue(float minBoostValue) {
    this.minBoostValue = minBoostValue;
  }

  public float getMaxBoostValue() {
    return maxBoostValue;
  }

  public void setMaxBoostValue(float maxBoostValue) {
    this.maxBoostValue = maxBoostValue;
  }

  public int getRecalculationInitialDelaySec() {
    return recalculationInitialDelaySec;
  }

  public void setRecalculationInitialDelaySec(int recalculationInitialDelaySec) {
    this.recalculationInitialDelaySec = recalculationInitialDelaySec;
  }

  public int getRecalculationIntervalSec() {
    return recalculationIntervalSec;
  }

  public void setRecalculationIntervalSec(int recalculationIntervalSec) {
    this.recalculationIntervalSec = recalculationIntervalSec;
  }

  public int getFailedCallMaxConsiderationTimeSec() {
    return failedCallMaxConsiderationTimeSec;
  }

  public void setFailedCallMaxConsiderationTimeSec(int failedCallMaxConsiderationTimeSec) {
    this.failedCallMaxConsiderationTimeSec = failedCallMaxConsiderationTimeSec;
  }

  public int getStoppedCampaignBoostPreservationTimeSec() {
    return stoppedCampaignBoostPreservationTimeSec;
  }

  public void setStoppedCampaignBoostPreservationTimeSec(int stoppedCampaignBoostPreservationTimeSec) {
    this.stoppedCampaignBoostPreservationTimeSec = stoppedCampaignBoostPreservationTimeSec;
  }

  public int getRecalculationIntervalMinCallsCount() {
    return recalculationIntervalMinCallsCount;
  }

  public void setRecalculationIntervalMinCallsCount(int recalculationIntervalMinCallsCount) {
    this.recalculationIntervalMinCallsCount = recalculationIntervalMinCallsCount;
  }

  public int getEmergencyRecalculationAbandonedCallsThreshold() {
    return emergencyRecalculationAbandonedCallsThreshold;
  }

  public void setEmergencyRecalculationAbandonedCallsThreshold(int emergencyRecalculationAbandonedCallsThreshold) {
    this.emergencyRecalculationAbandonedCallsThreshold = emergencyRecalculationAbandonedCallsThreshold;
  }

  public float getAbandonRateBoostDecreaseDeltaFactor() {
    return abandonRateBoostDecreaseDeltaFactor;
  }

  public void setAbandonRateBoostDecreaseDeltaFactor(float abandonRateBoostChangeDeltaFactor) {
    this.abandonRateBoostDecreaseDeltaFactor = abandonRateBoostChangeDeltaFactor;
  }

  public float getAnswerRateBoostDecreaseDeltaFactor() {
    return answerRateBoostDecreaseDeltaFactor;
  }

  public void setAnswerRateBoostDecreaseDeltaFactor(float answerRateBoostDecreaseDeltaFactor) {
    this.answerRateBoostDecreaseDeltaFactor = answerRateBoostDecreaseDeltaFactor;
  }

  public float getAnswerRateBoostIncreaseDeltaFactor() {
    return answerRateBoostIncreaseDeltaFactor;
  }

  public void setAnswerRateBoostIncreaseDeltaFactor(float answerRateBoostIncreaseDeltaFactor) {
    this.answerRateBoostIncreaseDeltaFactor = answerRateBoostIncreaseDeltaFactor;
  }

  public float getZeroAnswerRateTargetBoostMultiplier() {
    return zeroAnswerRateTargetBoostMultiplier;
  }

  public void setZeroAnswerRateTargetBoostMultiplier(float zeroAnswerRateBoostMultiplier) {
    this.zeroAnswerRateTargetBoostMultiplier = zeroAnswerRateBoostMultiplier;
  }

  private boolean publishOnRecalculation; // default false

  public boolean isPublishOnRecalculation() {
    return publishOnRecalculation;
  }
  public void setPublishOnRecalculation(boolean publishOnRecalculation) {
    this.publishOnRecalculation = publishOnRecalculation;
  }


  @Override
  public String toString() {
    return "DynamicBoostTestConfig{" +
        "defaultBoostValue=" + defaultBoostValue +
        ", minBoostValue=" + minBoostValue +
        ", maxBoostValue=" + maxBoostValue +
        ", recalculationInitialDelaySec=" + recalculationInitialDelaySec +
        ", recalculationIntervalSec=" + recalculationIntervalSec +
        ", failedCallMaxConsiderationTimeSec=" + failedCallMaxConsiderationTimeSec +
        ", stoppedCampaignBoostPreservationTimeSec=" + stoppedCampaignBoostPreservationTimeSec +
        ", recalculationIntervalMinCallsCount=" + recalculationIntervalMinCallsCount +
        ", abandonRateBoostDecreaseDeltaFactor=" + abandonRateBoostDecreaseDeltaFactor +
        ", answerRateBoostDecreaseDeltaFactor=" + answerRateBoostDecreaseDeltaFactor +
        ", answerRateBoostIncreaseDeltaFactor=" + answerRateBoostIncreaseDeltaFactor +
        ", zeroAnswerRateBoostMultiplier=" + zeroAnswerRateTargetBoostMultiplier +
        '}';
  }

}
