package com.powerdialer.exceptions;

/**
 * Exception for indicating empty filters
 */
public class EmptyFiltersWarningException extends WarningException {

  private static final long serialVersionUID = 8626153036812645407L;

  public EmptyFiltersWarningException(Object result) {
    super(result);
  }

}
