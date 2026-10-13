package com.powerdialer.engine.asterisk.events;

import org.asteriskjava.manager.event.UserEvent;

public class AniDefinitionEvent extends UserEvent {

  private String interactionId;
  private String parentId;

  public AniDefinitionEvent(Object source) {
    super(source);
  }

  @Override
  public String getUniqueId() {
    return super.getUniqueId();
  }

  public String getInteractionId() {
    return interactionId;
  }

  public void setInteractionId(String interactionId) {
    this.interactionId = interactionId;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }
}
