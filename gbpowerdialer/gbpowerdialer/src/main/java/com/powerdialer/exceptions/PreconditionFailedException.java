package com.powerdialer.exceptions;

import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Used to indicate that request is correct but some of conditions is not met
 */
@ResponseStatus(PRECONDITION_FAILED)
public class PreconditionFailedException extends RuntimeException {

  public PreconditionFailedException(String message) {
    super(message);
  }
}
