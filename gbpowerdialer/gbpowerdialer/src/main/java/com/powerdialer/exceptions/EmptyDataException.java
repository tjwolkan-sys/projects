package com.powerdialer.exceptions;

/**
 * Exception for indicating empty data object
 */
public class EmptyDataException extends RequestProcessingException {

  private static final long serialVersionUID = -1131625772491523920L;

  public EmptyDataException() {
    super("");
  }

}
