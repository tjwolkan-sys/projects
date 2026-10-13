package com.powerdialer.exceptions;

/**
 * Throw when creation of second global DNC for tenant is occurred
 */
public class NonUniqueGlobalDNC extends RuntimeException {

  private static final long serialVersionUID = 7250311048775976775L;

  public NonUniqueGlobalDNC() {
    super("Only one global DNC allowed per tenant");
  }

}
