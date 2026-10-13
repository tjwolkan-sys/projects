package com.powerdialer.engine.asterisk.events;

import org.asteriskjava.manager.event.UserEvent;

public class AgentPredictiveAnswerEvent extends UserEvent {

  private static final long serialVersionUID = 1L;
  private String parentId;
  private String username;

  public AgentPredictiveAnswerEvent(Object source) {
    super(source);
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

}
