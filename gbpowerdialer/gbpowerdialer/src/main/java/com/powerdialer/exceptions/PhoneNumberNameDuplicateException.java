package com.powerdialer.exceptions;

/**
 * Exception throws when CampaignValidator found that campaign include duplicates of Phone Number Columns
 */
public class PhoneNumberNameDuplicateException extends RuntimeException {

  public PhoneNumberNameDuplicateException() {
    super("Selected Phone Number for current campaign already exists");
  }
}
