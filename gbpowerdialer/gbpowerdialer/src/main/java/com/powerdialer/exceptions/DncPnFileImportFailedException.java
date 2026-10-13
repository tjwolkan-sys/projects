package com.powerdialer.exceptions;

public class DncPnFileImportFailedException extends RuntimeException {
  public DncPnFileImportFailedException() {
    super("File with DNC phone numbers to import was failed on upload.");
  }
}
