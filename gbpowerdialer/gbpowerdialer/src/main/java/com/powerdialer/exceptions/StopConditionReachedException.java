package com.powerdialer.exceptions;

public class StopConditionReachedException extends ArgumentativeException {

  private static final long serialVersionUID = -1804492083141660402L;

  public StopConditionReachedException(String message, String... arguments) {
    super(message);
    this.arguments = arguments;
  }

}
