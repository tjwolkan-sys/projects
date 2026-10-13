package com.powerdialer.exceptions;

public class AstServerConnectionException extends RuntimeException {

  private static final long serialVersionUID = 4794715893691588136L;

  public AstServerConnectionException(String message, Throwable cause) {
    super(message, cause);
  }

  public AstServerConnectionException(String message) {
    super(message);
  }

}
