package com.powerdialer.exceptions;

/**
 * Exception for indicating bad content of file. For example wrong JSON/XML
 * format
 */
public class BadContentException extends MultipartFileProcessingException {

  private static final long serialVersionUID = -2467424548964228562L;

  public BadContentException(String message) {
    super(message);
  }

  public BadContentException(String message, Throwable cause) {
    super(message, cause);
  }

}
