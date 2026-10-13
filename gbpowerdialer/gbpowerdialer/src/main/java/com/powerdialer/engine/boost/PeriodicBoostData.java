package com.powerdialer.engine.boost;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class PeriodicBoostData {

  private final Date calculationIntervalStartTime = new Date();
  private final AtomicInteger failedCallsCount = new AtomicInteger();
  private final AtomicInteger answeredCallsCount = new AtomicInteger();
  private final AtomicInteger abandonedCallsCount = new AtomicInteger();
  private final AtomicInteger handledCallsCount = new AtomicInteger();
  private float boostValue;

  public PeriodicBoostData(float boostValue) {
    this.boostValue = boostValue;
  }

  public Date getCalculationIntervalStartTime() {
    return calculationIntervalStartTime;
  }

  public float getBoostValue() {
    return boostValue;
  }

  public void setBoostValue(float boostValue) {
    this.boostValue = boostValue;
  }

  public AtomicInteger getFailedCallsCount() {
    return failedCallsCount;
  }

  public AtomicInteger getAnsweredCallsCount() {
    return answeredCallsCount;
  }

  public AtomicInteger getAbandonedCallsCount() {
    return abandonedCallsCount;
  }

  public AtomicInteger getHandledCallsCount() {
    return handledCallsCount;
  }

  @Override
  public String toString() {
    return "PeriodicBoostData{" +
        "calculationIntervalStartTime=" + calculationIntervalStartTime +
        ", failedCallsCount=" + failedCallsCount +
        ", answeredCallsCount=" + answeredCallsCount +
        ", abandonedCallsCount=" + abandonedCallsCount +
        ", handledCallsCount=" + handledCallsCount +
        ", boostValue=" + boostValue +
        '}';
  }

}
