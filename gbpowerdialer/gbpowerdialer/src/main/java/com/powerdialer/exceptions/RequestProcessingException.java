package com.powerdialer.exceptions;

/**
 * Intermediate base class for all problems encountered when processing request
 * by {@link com.powerdialer.service.CampaignDataManipulationService} or
 * {@link com.powerdialer.service.DNCDataManipulationService}
 */
public class RequestProcessingException extends RuntimeException {

  private static final long serialVersionUID = -938744070328106563L;

  public RequestProcessingException(String message) {
    super(message);
  }

  public RequestProcessingException(String message, Throwable cause) {
    super(message, cause);
  }

}
