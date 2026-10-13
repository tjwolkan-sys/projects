package com.powerdialer.exceptions;

/**
 * Exception for indicating problem with processing multipart file
 */
public class MultipartFileProcessingException extends RequestProcessingException {

  private static final long serialVersionUID = 5132719361439734755L;

  public MultipartFileProcessingException(String message) {
    super(message);
  }

  public MultipartFileProcessingException(String message, Throwable cause) {
    super(message, cause);
  }

}
