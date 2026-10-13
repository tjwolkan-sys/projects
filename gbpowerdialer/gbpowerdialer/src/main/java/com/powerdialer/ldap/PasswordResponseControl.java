/*
 * Copyright (c) Esmond Pitt, 2010. All rights reserved. Permission is hereby
 * granted to use this source code as-is provided this notice remains intact.
 */
package com.powerdialer.ldap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.naming.NamingException;
import javax.naming.ldap.BasicControl;

import netscape.ldap.ber.stream.BERChoice;
import netscape.ldap.ber.stream.BERElement;
import netscape.ldap.ber.stream.BEREnumerated;
import netscape.ldap.ber.stream.BERInteger;
import netscape.ldap.ber.stream.BERSequence;
import netscape.ldap.ber.stream.BERTag;
import netscape.ldap.ber.stream.BERTagDecoder;
import netscape.ldap.client.JDAPBERTagDecoder;

/**
 * PasswordResponseControl.
 * @author Esmond Pitt, Verismart Software Inc.
 * @see <a href=
 *      "http://tools.ietf.org/html/draft-behera-ldap-password-policy-09">the
 *      Internet Draft</a>
 */
public class PasswordResponseControl extends BasicControl {

  private static final long serialVersionUID = -85636453606837419L;
  /** PasswordResponse OID. */
  public static final String OID = "1.3.6.1.4.1.42.2.27.8.5.1";

  /** Warning codes. */
  public enum Warning {
                       timeBeforeExpiration,
                       graceAuthNsRemaining,
                       none
  }

  /** Error codes. */
  public enum Error {
                     /** The password has expired. */
                     passwordExpired,
                     accountLocked,
                     changeAfterReset,
                     passwordModNotAllowed,
                     mustSupplyOldPassword,
                     insufficientPasswordQuality,
                     passwordTooShort,
                     passwordTooYoung,
                     passwordInHistory,
                     /**
                      * No info, e.g. user has simply used the wrong password.
                      */
                     none
  }

  private int warningCode = Warning.none.ordinal();
  private int timeBeforeExpiration;
  private int graceLoginsRemaining;
  private int errorCode = Error.none.ordinal();

  /**
   * Construct an instance. Called by the PasswordResponseControlFactory.
   * @param isCritical true iff critical
   * @param ber BER encoded data
   * @throws NamingException Decoding problem.
   */
  public PasswordResponseControl(boolean isCritical, byte[] ber) throws NamingException {
    super(OID, isCritical, ber);
    try {
      new NetscapePasswordResponseControlDecoder().decode(ber);
    } catch (IOException exc) {
      throw new LdapDataAccessException("Failed to parse control value " + java.util.Arrays.toString(ber), exc);
    }
  }

  /**
   * @return true if this response has a Warning.
   */
  public boolean isWarningCode() {
    return warningCode != Warning.none.ordinal();
  }

  /**
   * Only meaningful if isWarningCode() returns true.
   * @return the Warning
   */
  public Warning getWarning() {
    return Warning.values()[warningCode];
  }

  /**
   * Only meaningful if isWarningCode() returns true and getWarning() returns
   * <code>Warning.graceAuthNsRemaining</code>.
   * @return the number of grace logins remaining.
   */
  public int getGraceLoginsRemaining() {
    return graceLoginsRemaining;
  }

  /**
   * Only meaningful if isWarningCode() returns true and getWarning() returns
   * <code>Warning.timeBeforeExpiration</code>.
   * @return the time to expiration, measured in seconds since the response that
   *         created this object was sent.
   */
  public int getTimeBeforeExpiration() {
    return timeBeforeExpiration;
  }

  /**
   * @return true if this response has an Error.
   */
  public boolean isErrorCode() {
    return errorCode != Error.none.ordinal();
  }

  /**
   * Only meaningful if isErrorCode() returns true.
   * @return the Error.
   */
  public Error getError() {
    return Error.values()[errorCode];
  }

  // toString() omitted for space reasons, use your imagination.
  /**
   * Decoder based on Netscape ldapsdk library.
   * @see Weltman and Dahbura, <i>LDAP Programming with Java,</i>, Addison
   *      Wesley 2000, ch. 16.
   */
  private class NetscapePasswordResponseControlDecoder {
    public void decode(byte[] encodedValue) throws IOException {
      int[] bytesRead = { 0 };
      BERSequence seq = (BERSequence) BERElement.getElement(new ApplicationTagDecoder(), new ByteArrayInputStream(encodedValue), bytesRead);
      for (int i = 0; i < seq.size(); i++) {
        BERTag tag = (BERTag) seq.elementAt(i);
        BERElement element = tag.getValue();
        if (element instanceof BERChoice) {
          // ASN.1 Choice: the warning data.
          BERChoice warning = (BERChoice) element;
          BERTag content = (BERTag) warning.getValue();
          int value = ((BERInteger) content.getValue()).getValue();
          warningCode = content.getTag();
          switch (warningCode) {
            case 0x80:
              timeBeforeExpiration = value;
              break;
            case 0x81:
              graceLoginsRemaining = value;
              break;
            default:
          }
          warningCode &= ~0x80; // Clear junk
        } else if (element instanceof BEREnumerated) {
          // ASN.1 Enumerated: the error code.
          BEREnumerated error = (BEREnumerated) element;
          errorCode = error.getValue();
        }
      }
    }

    /**
     * Application-specific decoder.
     */
    class ApplicationTagDecoder extends JDAPBERTagDecoder {
      private boolean inChoice;

      @Override
      public BERElement getElement(BERTagDecoder decoder, int tag, InputStream stream, int[] bytesRead, boolean[] implicit) throws IOException {
        switch (tag) {
          case 0xa0:
            implicit[0] = false;
            inChoice = true;
            BERElement.readLengthOctets(stream, bytesRead);
            int[] componentLength = new int[1];
            BERChoice choice = new BERChoice(decoder, stream, componentLength);
            bytesRead[0] += componentLength[0];
            return choice;
          case 0x80:
          case 0x81:
            if (inChoice) {
              // Must be time before expiry (0x80)
              // or graceLogins (0x81)
              inChoice = false;
              return new BERInteger(stream, bytesRead);
            }
            return new BEREnumerated(stream, bytesRead);
          default:
            return super.getElement(decoder, tag, stream, bytesRead, implicit);
        }
      }
    }
  }

}
