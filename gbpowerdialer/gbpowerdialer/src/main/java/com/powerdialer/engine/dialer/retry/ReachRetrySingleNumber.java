package com.powerdialer.engine.dialer.retry;

/**
 * Reach retry limit for single call, default retry strategy
 */
public class ReachRetrySingleNumber extends RetryStrategy {

  @Override
  public boolean isOverallRetryLimitReached() {
    return isCalledNumberInDNC() || !anyRetryLeft();
  }

  @Override
  public void setRetryNumber() {
    call.setRetryNumber(call.getCalledNumber());
  }

  /**
   * Task 325559 (karar 29.09.2026): bu strateji tek numarayi arar; sinirini zaten
   * {@code retryOveralAttempt} belirliyor. Yeni {@code maxAttemptPerNumber} alani burada bilincli
   * olarak YOK SAYILIR (strateji 1'deki K2 gibi). Uygulansaydi aranan numara limitini doldurunca
   * strateji diger numaralara gecer ve "tek numara" davranisi bozulurdu. UI'da alan ayrica freeze
   * edilir.
   */
  @Override
  protected boolean isPerNumberLimitActive() {
    return false;
  }

}
