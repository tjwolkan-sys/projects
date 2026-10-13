package com.powerdialer.exceptions;

/**
 * Thrown when campaign is not found
 */
public class CampaignNotFoundException extends NotFoundException {

  private static final long serialVersionUID = -693498474848900535L;

  public CampaignNotFoundException(String message) {
    super(message);
  }

}
