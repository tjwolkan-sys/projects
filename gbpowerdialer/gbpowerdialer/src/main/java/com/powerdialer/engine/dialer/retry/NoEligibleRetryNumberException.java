package com.powerdialer.engine.dialer.retry;

/**
 * Retry numara yuruyusu aday havuzunda aranabilir numara bulamadiginda
 * firlatilir. Guard (isOverallRetryLimitReached) ile numara secim yuruyusu
 * arasindaki invariant bozulsa bile thread sonsuz donguye girmez;
 * createRetry bu istisnayi yakalayip cagriyi complete eder.
 */
public class NoEligibleRetryNumberException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  public NoEligibleRetryNumberException(String message) {
    super(message);
  }

}
