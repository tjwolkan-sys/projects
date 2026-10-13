package com.powerdialer.exceptions;

/**
 * Exception for indicating problem with parsing filter condition
 */
public class BadFilterConditionException extends RequestProcessingException {

  private static final long serialVersionUID = -2601812402498880398L;

  public BadFilterConditionException(String message, Throwable cause) {
    super(message, cause);
  }

}
