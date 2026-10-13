package com.powerdialer.exceptions;

public abstract class ArgumentativeException extends RuntimeException {

  protected String[] arguments;

  public ArgumentativeException(String message, Throwable cause) {
    super(message, cause);
  }

  public ArgumentativeException(String message, String... arguments) {
    super(message);
    this.arguments = arguments;
  }

  public ArgumentativeException(String message) {
    super(message);
  }

  public String[] getArguments() {
    return arguments;
  }

}
