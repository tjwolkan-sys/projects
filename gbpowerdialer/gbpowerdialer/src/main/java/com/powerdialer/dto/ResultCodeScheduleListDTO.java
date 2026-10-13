package com.powerdialer.dto;

import java.util.Date;
import java.util.List;

import org.dozer.Mapping;

public class ResultCodeScheduleListDTO extends BaseEntityDTO {

  private ResultCodeFormDTO resultCode;
  private TelephonyResultCodeFormDTO telephonyResultCode;
  private int applyTo;
  private int retryAmount;
  private double retryInterval;
  private boolean advancedIntervalEnabled;
  private Date minScheduleTime;
  private Date maxScheduleTime;
  private boolean macroCycle;
  private Long campaign;
  private List<ResultCodeScheduleAdvancedFormDTO> resultCodeScheduleAdvanceds;

  public ResultCodeFormDTO getResultCode() {
    return resultCode;
  }

  public void setResultCode(ResultCodeFormDTO resultCode) {
    this.resultCode = resultCode;
  }

  public TelephonyResultCodeFormDTO getTelephonyResultCode() {
    return telephonyResultCode;
  }

  public void setTelephonyResultCode(TelephonyResultCodeFormDTO telephonyResultCode) {
    this.telephonyResultCode = telephonyResultCode;
  }

  public int getApplyTo() {
    return applyTo;
  }

  public void setApplyTo(int applyTo) {
    this.applyTo = applyTo;
  }

  public int getRetryAmount() {
    return retryAmount;
  }

  public void setRetryAmount(int retryAmount) {
    this.retryAmount = retryAmount;
  }

  public double getRetryInterval() {
    return retryInterval;
  }

  public void setRetryInterval(double retryInterval) {
    this.retryInterval = retryInterval;
  }

  public boolean isAdvancedIntervalEnabled() {
    return advancedIntervalEnabled;
  }

  public void setAdvancedIntervalEnabled(boolean advancedIntervalEnabled) {
    this.advancedIntervalEnabled = advancedIntervalEnabled;
  }

  public Date getMinScheduleTime() {
    return minScheduleTime;
  }

  public void setMinScheduleTime(Date minScheduleTime) {
    this.minScheduleTime = minScheduleTime;
  }

  public Date getMaxScheduleTime() {
    return maxScheduleTime;
  }

  public void setMaxScheduleTime(Date maxScheduleTime) {
    this.maxScheduleTime = maxScheduleTime;
  }

  public boolean isMacroCycle() {
    return macroCycle;
  }

  public void setMacroCycle(boolean macroCycle) {
    this.macroCycle = macroCycle;
  }

  @Mapping("campaign.id")
  public Long getCampaign() {
    return campaign;
  }

  public void setCampaign(Long campaign) {
    this.campaign = campaign;
  }

  public List<ResultCodeScheduleAdvancedFormDTO> getResultCodeScheduleAdvanceds() {
    return resultCodeScheduleAdvanceds;
  }

  public void setResultCodeScheduleAdvanceds(List<ResultCodeScheduleAdvancedFormDTO> resultCodeScheduleAdvanceds) {
    this.resultCodeScheduleAdvanceds = resultCodeScheduleAdvanceds;
  }

}
