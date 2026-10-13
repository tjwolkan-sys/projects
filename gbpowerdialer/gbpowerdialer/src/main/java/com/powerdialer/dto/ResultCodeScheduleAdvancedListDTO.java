package com.powerdialer.dto;

import org.dozer.Mapping;

public class ResultCodeScheduleAdvancedListDTO extends BaseEntityDTO {

  private Long resultCodeSchedule;
  private int dialAttempt;
  private double retryInterval;
  private int numberToTry;

  @Mapping("resultCodeSchedule.id")
  public Long getResultCodeSchedule() {
    return resultCodeSchedule;
  }

  public void setResultCodeSchedule(Long resultCodeSchedule) {
    this.resultCodeSchedule = resultCodeSchedule;
  }

  public int getDialAttempt() {
    return dialAttempt;
  }

  public void setDialAttempt(int dialAttempt) {
    this.dialAttempt = dialAttempt;
  }

  public double getRetryInterval() {
    return retryInterval;
  }

  public void setRetryInterval(double retryInterval) {
    this.retryInterval = retryInterval;
  }

  public int getNumberToTry() {
    return numberToTry;
  }

  public void setNumberToTry(int numberToTry) {
    this.numberToTry = numberToTry;
  }

}
