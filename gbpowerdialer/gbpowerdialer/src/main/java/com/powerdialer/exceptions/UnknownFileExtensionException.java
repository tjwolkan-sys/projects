package com.powerdialer.exceptions;

/**
 * Exception for indicating unknown file extension
 */
public class UnknownFileExtensionException extends MultipartFileProcessingException {

  private static final long serialVersionUID = -4466114818926738674L;

  public UnknownFileExtensionException(String message) {
    super(message);
  }

}
