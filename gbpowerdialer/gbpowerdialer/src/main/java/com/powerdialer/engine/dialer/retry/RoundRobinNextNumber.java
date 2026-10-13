package com.powerdialer.engine.dialer.retry;

/**
 * Round-robin dial with overall retry limit, schedule next number, (Retry rules
 * applied overall dial attempts. Next attempt will use next number and cycle
 * among them till retry limit reached)
 */
public class RoundRobinNextNumber extends RetryStrategy {

  @Override
  public boolean isOverallRetryLimitReached() {
    if (!anyNumberPresentAfterDNC())
      return true;

    return !anyRetryLeft();
  }

  @Override
  public void setRetryNumber() {
    call.setRetryNumber(call.getNextNumberFromRawData());
    processRetryNumberWithDNC();
  }

}
