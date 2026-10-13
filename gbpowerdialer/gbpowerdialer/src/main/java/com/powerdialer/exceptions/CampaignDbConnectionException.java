package com.powerdialer.exceptions;

public class CampaignDbConnectionException extends RuntimeException {

  private static final long serialVersionUID = 6890529541256372219L;

  public CampaignDbConnectionException(String message, Throwable cause) {
    super(message, cause);
  }

  public CampaignDbConnectionException(String message) {
    super(message);
  }

}
