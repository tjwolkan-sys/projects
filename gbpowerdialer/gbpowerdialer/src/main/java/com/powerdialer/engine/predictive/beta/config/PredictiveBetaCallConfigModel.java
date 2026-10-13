package com.powerdialer.engine.predictive.beta.config;

public class PredictiveBetaCallConfigModel {

  private int recalculationInitialDelaySec;
  private int recalculationIntervalSec;
  private float defaultTotalNumberOfCallsValue;

  public float getDefaultTotalNumberOfCallsValue() {
    return defaultTotalNumberOfCallsValue;
  }

  public void setDefaultTotalNumberOfCallsValue(float defaultTotalNumberOfCallsValue) {
    this.defaultTotalNumberOfCallsValue = defaultTotalNumberOfCallsValue;
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

  @Override
  public String toString() {
    return "PredictiveBetaCallConfigModel{" +
        ", recalculationInitialDelaySec=" + recalculationInitialDelaySec +
        ", recalculationIntervalSec=" + recalculationIntervalSec +
        ", defaultTotalNumberOfCallsValue=" + defaultTotalNumberOfCallsValue +
        '}';
  }

}

