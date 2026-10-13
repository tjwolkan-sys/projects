package com.powerdialer.exceptions;

public class LdapBadCredentialsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LdapBadCredentialsException() {
    super("Bad ldap credentials");
  }

}
