package com.powerdialer.exceptions;

import org.springframework.security.core.AuthenticationException;

public class PasswordShallBeChangedException extends AuthenticationException {
  public PasswordShallBeChangedException(String msg) {
    super(msg);
  }

  public PasswordShallBeChangedException(String msg, Throwable t) {
    super(msg, t);
  }
}
