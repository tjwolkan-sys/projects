package com.powerdialer.engine.asterisk.events;

import org.asteriskjava.manager.event.UserEvent;

/**
 * Custom <code>UserEvent</code> implementation. See <code>UserEvent</code> for
 * details. Class is used to get variables: queueName, parentid for currentChannel
 * @author OAfanasenko
 *
 */
public class VariablesEvent extends UserEvent {

  private static final long serialVersionUID = 1L;

  public VariablesEvent(Object source) {
    super(source);
  }
  
  private String queueName;
  private String parentid;
  private String supervisorAction;
  private String fromnum;
  private String agentchannel;
  private String clientchannel;
  private String agent;

  @Override
  public String getUniqueId() {
    return super.getUniqueId();
  }

  public String getQueueName() {
    return queueName;
  }
  
  public void setQueueName(String queueName) {
    this.queueName = queueName;
  }
  
  public String getParentid() {
    return parentid;
  }
  
  public void setParentid(String parentid) {
    this.parentid = parentid;
  }

  public String getSupervisorAction() {
    return supervisorAction;
  }

  public void setSupervisorAction(String supervisorAction) {
    this.supervisorAction = supervisorAction;
  }

  public String getFromnum() {
    return fromnum;
  }

  public void setFromnum(String fromnum) {
    this.fromnum = fromnum;
  }

  public String getAgentchannel() {
    return agentchannel;
  }

  public void setAgentchannel(String agentchannel) {
    this.agentchannel = agentchannel;
  }

  public String getClientchannel() {
    return clientchannel;
  }

  public void setClientchannel(String clientchannel) {
    this.clientchannel = clientchannel;
  }

  public String getAgent() {
    return agent;
  }

  public void setAgent(String agent) {
    this.agent = agent;
  }

}
