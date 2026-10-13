package com.powerdialer.engine.asterisk.events;

import org.asteriskjava.manager.event.UserEvent;

/**
 * Custom <code>UserEvent</code> implementation. See <code>UserEvent</code> for
 * details. 
 * If call is inbound and this call ended by customer - this event is
 * null, if this call ended by agent - event not null. 
 * On the other hand, if call
 * is outbound and this call ended by customer - event not null, if call ended
 * by agent - event is null
 * @author Taras Gerasymchuk
 */
public class DisconnectEvent extends UserEvent {

  private static final long serialVersionUID = 1L;

  public DisconnectEvent(Object source) {
    super(source);
  }

  private String initiator;

  /**
   * Destination call uniqueId
   * @return uniqueId
   */
  @Override
  public String getUniqueId() {
    return super.getUniqueId();
  }

  public String getInitiator() {
    return initiator;
  }

  public void setInitiator(String initiator) {
    this.initiator = initiator;
  }

}
