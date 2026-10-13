/*
 * Copyright (c) Esmond Pitt, 2010. All rights reserved. Permission is hereby
 * granted to use this source code as-is provided this notice remains intact.
 */
package com.powerdialer.ldap;

import javax.naming.ldap.BasicControl;

/**
 * PasswordRequestControl.
 * @author Esmond Pitt
 */
public class PasswordRequestControl extends BasicControl {
  public static final long serialVersionUID = 5342767500282457716L;

  // @see
  // http://tools.ietf.org/html/draft-behera-ldap-password-policy-10#page-24
  public PasswordRequestControl() {
    super(PasswordResponseControl.OID);
  }

  public PasswordRequestControl(boolean isCritical) {
    super(PasswordResponseControl.OID, isCritical, null);
  }

}
