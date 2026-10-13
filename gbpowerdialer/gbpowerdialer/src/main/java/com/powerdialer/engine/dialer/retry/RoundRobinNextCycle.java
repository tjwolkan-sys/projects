package com.powerdialer.engine.dialer.retry;

/**
 * Round-robin dial with overall retry limit, schedule next cycle, (Retry rules
 * applied overall dial attempts. Next number will be dialed immediately if no
 * Final Result, schedule interval rules applied before beginning new cycle)
 */
public class RoundRobinNextCycle extends RoundRobinPerCycle {

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
