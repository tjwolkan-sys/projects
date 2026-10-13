package com.powerdialer.exceptions;

/**
 * Exception is thrown when request's column name is not equals any campaign's
 * table's columns
 */
public class BadRequestColumnName extends RequestProcessingException {
  public BadRequestColumnName(String message) {
    super(message);
  }
}
