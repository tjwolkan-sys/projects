package com.powerdialer.engine.asterisk.events;

import org.asteriskjava.manager.event.UserEvent;

/**
 * Custom <code>UserEvent</code> implementation. See <code>UserEvent</code> for
 * details.
 */
public class DialerHangupEvent extends UserEvent {

  private static final long serialVersionUID = -7352849917066472047L;
  private String originId;
  private String hangupCause;

  public DialerHangupEvent(Object source) {
    super(source);
  }

  public String getOriginId() {
    return originId;
  }

  public void setOriginId(String originId) {
    this.originId = originId;
  }

  public String getHangupCause() {
    return hangupCause;
  }

  public void setHangupCause(String hangupCause) {
    this.hangupCause = hangupCause;
  }

}
