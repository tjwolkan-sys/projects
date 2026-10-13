/*
 * Copyright (c) Esmond Pitt, 2010. All rights reserved. Permission is hereby
 * granted to use this source code as-is provided this notice remains intact.
 */
package com.powerdialer.ldap;

import javax.naming.NamingException;

/**
 * Ldap data parsing exception.
 * @author Esmond Pitt
 */
public class LdapDataAccessException extends NamingException {

  private static final long serialVersionUID = 1508494420728837135L;

  public LdapDataAccessException(String msg) {
    super(msg);
  }

  public LdapDataAccessException(String msg, Exception exc) {
    super(msg + ": " + exc.getMessage());
    super.setRootCause(exc);
    super.setStackTrace(exc.getStackTrace());
  }

}
