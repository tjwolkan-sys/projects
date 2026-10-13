package com.powerdialer.engine.asterisk.events;

import org.asteriskjava.manager.event.UserEvent;

/**
 * Custom <code>UserEvent</code> implementation. See <code>UserEvent</code> for
 * details.
 */
public class HardphoneAnswerEvent extends UserEvent {

  private static final long serialVersionUID = 1L;

  public HardphoneAnswerEvent(Object source) {
    super(source);
  }

  private String agentchannel;
  private String agent;

  @Override
  public String getUniqueId() {
    return super.getUniqueId();
  }

  public String getAgentchannel() {
    return agentchannel;
  }

  public void setAgentchannel(String agentchannel) {
    this.agentchannel = agentchannel;
  }

  public String getAgent() {
    return agent;
  }

  public void setAgent(String agent) {
    this.agent = agent;
  }

}
