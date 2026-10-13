package com.powerdialer.engine.boost;

public class TestCallsEmulationParams {

  private int answeredCallsMin;
  private int answeredCallsMax;
  private int failedCallsMin;
  private int failedCallsMax;
  private int abandonedCallsMin;
  private int abandonedCallsMax;
  private int handledCallsMin;
  private int handledCallsMax;

  public int getAnsweredCallsMin() {
    return answeredCallsMin;
  }

  public void setAnsweredCallsMin(int answeredCallsMin) {
    this.answeredCallsMin = answeredCallsMin;
  }

  public int getAnsweredCallsMax() {
    return answeredCallsMax;
  }

  public void setAnsweredCallsMax(int answeredCallsMax) {
    this.answeredCallsMax = answeredCallsMax;
  }

  public int getFailedCallsMin() {
    return failedCallsMin;
  }

  public void setFailedCallsMin(int failedCallsMin) {
    this.failedCallsMin = failedCallsMin;
  }

  public int getFailedCallsMax() {
    return failedCallsMax;
  }

  public void setFailedCallsMax(int failedCallsMax) {
    this.failedCallsMax = failedCallsMax;
  }

  public int getAbandonedCallsMin() {
    return abandonedCallsMin;
  }

  public void setAbandonedCallsMin(int abandonedCallsMin) {
    this.abandonedCallsMin = abandonedCallsMin;
  }

  public int getAbandonedCallsMax() {
    return abandonedCallsMax;
  }

  public void setAbandonedCallsMax(int abandonedCallsMax) {
    this.abandonedCallsMax = abandonedCallsMax;
  }

  public int getHandledCallsMin() {
    return handledCallsMin;
  }

  public void setHandledCallsMin(int handledCallsMin) {
    this.handledCallsMin = handledCallsMin;
  }

  public int getHandledCallsMax() {
    return handledCallsMax;
  }

  public void setHandledCallsMax(int handledCallsMax) {
    this.handledCallsMax = handledCallsMax;
  }

  @Override
  public String toString() {
    return "TestCallsEmulationRequest{" +
        "answeredCalls=(" + answeredCallsMin + " - " + answeredCallsMax +
        "), failedCalls=(" + failedCallsMin + " - " + failedCallsMax +
        "), abandonedCalls=(" + abandonedCallsMin + " - " + abandonedCallsMax +
        "), handledCalls=(" + handledCallsMin + " - " + handledCallsMax +
        ")}";
  }
}
