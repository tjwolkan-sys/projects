package com.powerdialer.engine.dialer.retry;

import java.util.Calendar;
import java.util.Date;

import com.powerdialer.model.ResultCodeSchedule;
import com.powerdialer.model.ResultCodeScheduleAdvanced;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Utils;

/**
 * Retry rules manages cycle (Retry management defines which number will be
 * dialed next. Overall retry limits applied.)
 */
public class RetryRulesCycle extends RetryStrategy {

  private ResultCodeSchedule resultCodeSchedule;
  private ResultCodeScheduleAdvanced resultCodeScheduleAdvanced;

  @Override
  public boolean isOverallRetryLimitReached() {
    if (!anyNumberPresentAfterDNC())
      return true;

    if (resultCodeSchedule == null && isCalledNumberInDNC())
      return true;

    if (isRetryLimitReachedForResultCodeSchedule())
      return true;

    return !anyRetryLeft();
  }

  private boolean isRetryLimitReachedForResultCodeSchedule() {
    return resultCodeSchedule != null && call.getResultCodeSequence() > resultCodeSchedule.getRetryAmount();
  }

  @Override
  public boolean isCallAnswered() {
    if (call.getUserResultCode() != null)
      findResultCodeScheduleHavingResultCode();

    if (resultCodeSchedule == null && call.getTelephonyResultCode() != null) {
      findResultCodeScheduleHavingTelephonyResultCode();
      return resultCodeSchedule == null && call.getTelephonyResultCode().getCode() == Constants.TELEPHONY_RC_NORMAL_CLEARING;
    }

    return false;
  }

  /**
   * Find resultCodeSchedule for user result code of call
   */
  private void findResultCodeScheduleHavingResultCode() {
    this.resultCodeSchedule = campaign.getResultCodeSchedules().stream()
        .filter(ResultCodeSchedule::isEnabled)
        .filter(rcs -> rcs.getResultCode() != null)
        .filter(rcs -> rcs.getResultCode().equals(call.getUserResultCode()))
        .filter(this::isResultCodeAppliesToRetries)
        .findFirst()
        .orElse(null);
  }

  /**
   * Find resultCodeSchedule for telephony result code of call
   */
  private void findResultCodeScheduleHavingTelephonyResultCode() {
    this.resultCodeSchedule = campaign.getResultCodeSchedules().stream()
        .filter(ResultCodeSchedule::isEnabled)
        .filter(rcs -> rcs.getTelephonyResultCode() != null)
        .filter(rcs -> rcs.getTelephonyResultCode().equals(call.getTelephonyResultCode()))
        .filter(this::isResultCodeAppliesToRetries)
        .findFirst()
        .orElse(null);
  }

  /**
   * Check if given result code can be applied to retries
   * @param resultCodeSchedule
   * @return true if applies, else false
   */
  private boolean isResultCodeAppliesToRetries(ResultCodeSchedule resultCodeSchedule) {
    return resultCodeSchedule.getApplyTo().equals(Constants.APPLY_TO.RETRIES) || resultCodeSchedule.getApplyTo().equals(Constants.APPLY_TO.RETRIES_AND_CALLBACKS);
  }

  @Override
  public void setRetryNumber() {
    String retryNumber = call.getCalledNumber();
    if (resultCodeSchedule != null && resultCodeSchedule.isAdvancedIntervalEnabled()) {
      findResultCodeScheduleAdvanced();
      if (resultCodeScheduleAdvanced != null)
        retryNumber = findRetryNumber();
    }
    call.setRetryNumber(retryNumber);
    processRetryNumberWithDNC();
  }

  /**
   * Find resultCodeScheduleAdvanced for this telephony result code
   */
  private void findResultCodeScheduleAdvanced() {
    this.resultCodeScheduleAdvanced = resultCodeSchedule.getResultCodeScheduleAdvanceds().stream()
        .filter(ResultCodeScheduleAdvanced::isEnabled)
        .filter(rcsa -> rcsa.getDialAttempt() == call.getResultCodeSequence())
        .findFirst()
        .orElse(null);
  }

  /**
   * Find which number to call next
   * @return number to retry
   */
  private String findRetryNumber() {
    String retryNumber;
    switch (resultCodeScheduleAdvanced.getNumberToTry()) {
      case NEXT:
        retryNumber = call.getNextNumberFromRawData();
        break;
      case PREVIOUS:
        retryNumber = call.getPreviousNumberFromRawData();
        break;
      case SAME:
        retryNumber = call.getCalledNumber();
        break;
      default:
        retryNumber = call.getCalledNumber();
        break;
    }
    return retryNumber;
  }

  @Override
  public void setRetryDateAndIncrementRetryCounts() {
    double plannedRetryDate = calculatePlannedRetryDate();

    if ((resultCodeSchedule != null && resultCodeSchedule.isMacroCycle() && isRetryLimitInMacroReached()) || (resultCodeSchedule == null && isRetryLimitInMacroReached()))
      plannedRetryDate += (campaign.getMacroInterval() / 24D);

    if (isPlannedRetryDateExceedScope(plannedRetryDate))
      plannedRetryDate = calculateRetryDateFromNextDay(plannedRetryDate);

    call.setRetryDate(plannedRetryDate);
  }

  @Override
  public double calculatePlannedRetryDate() {
    Calendar plannedRetryDate = Calendar.getInstance();
    plannedRetryDate.setTime(new Date());

    if (resultCodeSchedule == null && campaign.getRetryDelay() != null) {
      plannedRetryDate.add(Calendar.SECOND, (int) Math.round(campaign.getRetryDelay() * 60));
    } else {
      if (resultCodeScheduleAdvanced == null) {
        plannedRetryDate.add(Calendar.SECOND, (int) Math.round(resultCodeSchedule.getRetryInterval() * 60));
      } else {
        plannedRetryDate.add(Calendar.SECOND, (int) Math.round(resultCodeScheduleAdvanced.getRetryInterval() * 60));
      }
    }

    double plannedRetryDateAsDouble = Utils.convertDateToDouble(plannedRetryDate.getTime());
    return plannedRetryDateAsDouble;
  }

  @Override
  public boolean isPlannedRetryDateExceedScope(Double plannedRetryDate) {
    return isCallTillTimeExceeded(plannedRetryDate) || isResultCodeScheduleMaxTimeExceeded(plannedRetryDate);
  }

  /**
   * Check if planned retry date for call is after call's till time
   * @param plannedRetryDate
   * @return true if plannedRetry date is after call till time, else false
   */
  private boolean isCallTillTimeExceeded(Double plannedRetryDate) {
    Double canSetRetryTill = call.getCallToTimeFromRawData();
    return (canSetRetryTill != null && plannedRetryDate != null) && (canSetRetryTill < plannedRetryDate % 1);
  }

  /**
   * Check if planned retry is after max schedule time of result code schedule
   * @param plannedRetryDate
   * @return true if plannedRetry date is after max schedule time
   */
  private boolean isResultCodeScheduleMaxTimeExceeded(Double plannedRetryDate) {
    if (resultCodeSchedule != null) {
      double canSetRetryTill = Utils.convertDateToDouble(resultCodeSchedule.getMaxScheduleTime()) % 1;
      return canSetRetryTill < plannedRetryDate % 1;
    }
    return false;
  }

  @Override
  public double calculateRetryDateFromNextDay(Double plannedRetryDate) {
    long nextDay = ((long) plannedRetryDate.doubleValue()) + 1;
    return resultCodeSchedule == null ? nextDay + call.getCallFromTimeFromRawData() : nextDay + (Utils.convertDateToDouble(resultCodeSchedule.getMinScheduleTime()) % 1);
  }

}
