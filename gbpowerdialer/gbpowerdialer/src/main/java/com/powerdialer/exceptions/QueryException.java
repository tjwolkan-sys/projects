package com.powerdialer.exceptions;

public class QueryException extends RuntimeException {

  private static final long serialVersionUID = -2624402562013806070L;

  public QueryException(String message, Throwable cause) {
    super(message, cause);
  }

  public QueryException(String message) {
    super(message);
  }

}
