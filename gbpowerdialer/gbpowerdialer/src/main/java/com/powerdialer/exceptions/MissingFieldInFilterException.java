package com.powerdialer.exceptions;

/**
 * Exception for indicating missing field in filter
 */
public class MissingFieldInFilterException extends RequestProcessingException {

  private static final long serialVersionUID = 1231812402498184308L;

  public MissingFieldInFilterException(String message) {
    super(message);
  }

}
