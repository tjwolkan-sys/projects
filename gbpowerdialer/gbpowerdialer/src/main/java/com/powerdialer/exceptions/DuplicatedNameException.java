package com.powerdialer.exceptions;

public class DuplicatedNameException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DuplicatedNameException() {
    super("Name already exists");
  }

}
