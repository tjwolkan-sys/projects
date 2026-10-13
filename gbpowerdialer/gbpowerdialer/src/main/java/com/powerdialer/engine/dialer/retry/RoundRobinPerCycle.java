package com.powerdialer.engine.dialer.retry;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.BiFunction;

import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.util.Utils;

/**
 * Round-robin dial with per cycle retry limit, schedule next cycle, (Retry
 * rules applied per cycle. Next number will be dialed immediately if no Final
 * Result, schedule interval rules applied before beginning new cycle)
 */
public class RoundRobinPerCycle extends RetryStrategy {

  @Override
  public boolean isOverallRetryLimitReached() {
    if (isAttemptsLimitReached())
      return true;

    return isLimitReached((retryTotal, numberSizeInRawData) -> {
      return retryTotal >= campaign.getRetryOveralAttempt() * numberSizeInRawData;
    });
  }

  @Override
  public void setRetryNumber() {
    call.setRetryNumber(calculateRetryNumber());
    processRetryNumberWithDNC();
  }

  private String calculateRetryNumber() {
    List<PhoneNumberColumn> numberColumns = call.getNumberColumnsInRawData();
    int retriesCount = call.getRetryHistory().size() + 1; // +1 is for first call
    int index = retriesCount % numberColumns.size();
    return call.getRawData().getString(numberColumns.get(index).getColNumber());
  }

  @Override
  public double calculatePlannedRetryDate() {
    Calendar plannedRetryDate = Calendar.getInstance();
    plannedRetryDate.setTime(new Date());

    if (isRetryCycleReached() && campaign.getRetryDelay() != null)
      plannedRetryDate.add(Calendar.SECOND, (int) Math.round(campaign.getRetryDelay() * 60));

    double plannedRetryDateAsDouble = Utils.convertDateToDouble(plannedRetryDate.getTime());
    return plannedRetryDateAsDouble;
  }

  public boolean isRetryCycleReached() {
    int numberSizeInRawData = call.getNumberColumnsInRawData().size() - call.getDNCNumbers().size();

    if (numberSizeInRawData == 1)
      return true;

    if (call.getRetryHistory().size() > 0) {
      int totalCallCount = call.getRetryHistory().size() + 1;
      return totalCallCount % numberSizeInRawData == 0;
    }

    return false;
  }

  @Override
  public boolean isRetryLimitInMacroReached() {
    return isLimitReached((retryTotal, numberSizeInRawData) -> {
      return retryTotal > 0 && retryTotal % (campaign.getMacroMaxAttempt() * numberSizeInRawData) == 0;
    });
  }

  private boolean isLimitReached(BiFunction<Integer, Integer, Boolean> limitFormula) {
    int numberSizeInRawData = call.getNumberColumnsInRawData().size() - call.getDNCNumbers().size();
    int callTotal = call.getRetryHistory().size() + 1;
    int retryTotal = callTotal > numberSizeInRawData ? callTotal - numberSizeInRawData : 0;
    return limitFormula.apply(retryTotal, numberSizeInRawData);
  }

  private boolean isAttemptsLimitReached() {
    Integer retryOveralAttempt = campaign.getRetryOveralAttempt();
    return retryOveralAttempt != null && retryOveralAttempt > 0 && call.getAttempts() >= retryOveralAttempt;
  }

}
