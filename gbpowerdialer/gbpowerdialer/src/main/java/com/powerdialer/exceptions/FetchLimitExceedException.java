package com.powerdialer.exceptions;

/**
 * Exception for indicating exceeding fetch limit. Limit is set by constant
 * {@link com.powerdialer.util.Constants#APP_CONFIG_REST_FETCH_LIMIT}
 */
public class FetchLimitExceedException extends RequestProcessingException {

  private static final long serialVersionUID = 8524889292822216294L;

  public FetchLimitExceedException(String message) {
    super(message);
  }

}
