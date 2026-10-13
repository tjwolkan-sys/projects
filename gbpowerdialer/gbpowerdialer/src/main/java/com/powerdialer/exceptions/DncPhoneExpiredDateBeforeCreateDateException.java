package com.powerdialer.exceptions;

public class DncPhoneExpiredDateBeforeCreateDateException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public DncPhoneExpiredDateBeforeCreateDateException() {
    super("Phone number expire date cannot be earlier than add date.");
  }
}
