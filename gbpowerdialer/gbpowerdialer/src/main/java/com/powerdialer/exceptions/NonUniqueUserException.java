package com.powerdialer.exceptions;


/**
 * Thrown when hibernate throws NonUniqueResultException.
 * That means that there are more then one user with typed username.
 */
public class NonUniqueUserException extends RuntimeException {

  public NonUniqueUserException(String message, Throwable cause) {
    super(message, cause);
  }
}
