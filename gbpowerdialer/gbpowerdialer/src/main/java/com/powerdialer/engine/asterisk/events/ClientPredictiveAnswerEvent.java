package com.powerdialer.engine.asterisk.events;

import org.asteriskjava.manager.event.UserEvent;

/**
 * Custom <code>UserEvent</code> implementation. See <code>UserEvent</code> for
 * details.
 */
public class ClientPredictiveAnswerEvent extends UserEvent {

  private static final long serialVersionUID = 1L;
  private String parentId;

  public ClientPredictiveAnswerEvent(Object source) {
    super(source);
  }

  private String clientchannel;

  @Override
  public String getUniqueId() {
    return super.getUniqueId();
  }

  public String getClientchannel() {
    return clientchannel;
  }

  public void setClientchannel(String clientchannel) {
    this.clientchannel = clientchannel;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }
}
