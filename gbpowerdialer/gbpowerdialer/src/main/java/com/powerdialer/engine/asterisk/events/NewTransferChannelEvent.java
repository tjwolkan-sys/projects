package com.powerdialer.engine.asterisk.events;

import org.asteriskjava.manager.event.UserEvent;

/**
 * Asterisk dialplan tarafından transfer tetiklendiğinde fire edilen UserEvent.
 * `LinkedChannel` alanı çağrının Asterisk LINKEDID'sini taşır; dialer
 * `Call.linkedid` (VarSetEvent.uniqueId) üzerinden eşleştirme yapar.
 */
public class NewTransferChannelEvent extends UserEvent {

  private static final long serialVersionUID = 1L;

  private String linkedChannel;
  private String target;
  private String eventChannel;

  public NewTransferChannelEvent(Object source) {
    super(source);
  }

  public String getLinkedChannel() {
    return linkedChannel;
  }

  public void setLinkedChannel(String linkedChannel) {
    this.linkedChannel = linkedChannel;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public String getEventChannel() {
    return eventChannel;
  }

  public void setEventChannel(String eventChannel) {
    this.eventChannel = eventChannel;
  }
}
