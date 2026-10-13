/*
 * Copyright (c) Esmond Pitt, 2010. All rights reserved. Permission is hereby
 * granted to use this source code as-is provided this notice remains intact.
 */
package com.powerdialer.ldap;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.ldap.Control;
import javax.naming.ldap.ControlFactory;

/**
 * Factory for PasswordResponseControl.
 * @author Esmond Pitt
 */
public class PasswordResponseControlFactory extends ControlFactory {
  private Logger logger = Logger.getLogger(this.getClass().getName());

  public PasswordResponseControlFactory() {
    logger.fine("constructed");
  }

  @Override
  public Control getControlInstance(Control control) throws NamingException {
    logger.log(Level.FINE, "control ID={0} BER={1}", new Object[] { control.getID(), Arrays.toString(control.getEncodedValue()) });
    String id = control.getID();

    // See if it's one of ours
    // It will only be a response control!
    if (id.equals(PasswordResponseControl.OID)) {
      return new PasswordResponseControl(control.isCritical(), control.getEncodedValue());
    }
    return null;
  }

}
