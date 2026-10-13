package com.powerdialer.exceptions;

public class DateMismatchException extends ArgumentativeException {

  private static final long serialVersionUID = -1804492083141660402L;

  public DateMismatchException(String message, String... arguments) {
    super(message);
    this.arguments = arguments;
  }

  public DateMismatchException(String message) {
    super(message);
  }

}
