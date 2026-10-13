package com.powerdialer.engine.asterisk.events;

import org.asteriskjava.manager.event.UserEvent;

public class AgentCalledByDialerEvent extends UserEvent {

  private static final long serialVersionUID = 1L;

  public AgentCalledByDialerEvent(Object source) {
    super(source);
  }

  private String agent;
  private String dialedNumber;
  private String parentId;

  @Override
  public String getUniqueId() {
    return super.getUniqueId();
  }

  public String getAgent() {
    return agent;
  }

  public void setAgent(String agent) {
    this.agent = agent;
  }

  public String getDialedNumber() {
    return dialedNumber;
  }

  public void setDialedNumber(String dialedNumber) {
    this.dialedNumber = dialedNumber;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }
}
