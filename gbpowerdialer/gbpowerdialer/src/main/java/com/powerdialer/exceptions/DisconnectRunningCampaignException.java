package com.powerdialer.exceptions;

/**
 * Exception throws when client tries to disconnect Campaign which in "IS RUNNING" status
 */
public class DisconnectRunningCampaignException extends RuntimeException {

  public DisconnectRunningCampaignException() {
    super("Can't disconnect campaign while it's running");
  }
}
