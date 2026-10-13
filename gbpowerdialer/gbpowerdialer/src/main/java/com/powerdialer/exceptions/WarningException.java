package com.powerdialer.exceptions;

/**
 * Exceptions for some suspicious actions is encountered
 */
public class WarningException extends RequestProcessingException {

  private static final long serialVersionUID = -7285733424861499955L;
  private final Object result;

  public WarningException(Object result) {
    super("");
    this.result = result;
  }

  public Object getResult() {
    return result;
  }

}
