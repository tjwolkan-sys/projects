package com.powerdialer.exceptions;

import java.util.Arrays;

public class CampaignIllegalStateException extends ArgumentativeException {

  private static final long serialVersionUID = 5689408755365824402L;

  public CampaignIllegalStateException(String message, String... arguments) {
    super(message);
    this.arguments = arguments;
  }

  @Override
  public String toString() {
    return super.toString() + " " + Arrays.toString(arguments);
  }

}
