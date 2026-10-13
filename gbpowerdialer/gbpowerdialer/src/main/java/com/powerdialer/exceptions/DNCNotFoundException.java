package com.powerdialer.exceptions;

/**
 * Thrown when DNC is not found
 */
public class DNCNotFoundException extends NotFoundException {

  private static final long serialVersionUID = -7369832310243809807L;

  public DNCNotFoundException(String message) {
    super(message);
  }

}
