package com.powerdialer.exceptions;

/**
 * Thrown when required object is not found
 */
public class NotFoundException extends RequestProcessingException {

  private static final long serialVersionUID = -2322168052423408032L;

  public NotFoundException(String message) {
    super(message);
  }

}
