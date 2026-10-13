package com.powerdialer.redis.message;

import java.util.UUID;

import com.powerdialer.util.Constants;


public class RdsConfigurationMessage {

  private Constants.RDS_MESSAGE_ACTION_TYPE actionType;
  private Long routingGroupId;
  private UUID publisher;

  public RdsConfigurationMessage() {
  }

  public RdsConfigurationMessage(Constants.RDS_MESSAGE_ACTION_TYPE actionType, Long routingGroupId) {
    this.actionType = actionType;
    this.routingGroupId = routingGroupId;
  }

  public Constants.RDS_MESSAGE_ACTION_TYPE getActionType() {
    return actionType;
  }

  public void setActionType(Constants.RDS_MESSAGE_ACTION_TYPE actionType) {
    this.actionType = actionType;
  }

  public Long getRoutingGroupId() {
    return routingGroupId;
  }

  public void setRoutingGroupId(Long routingGroupId) {
    this.routingGroupId = routingGroupId;
  }

  public UUID getPublisher() {
    return publisher;
  }

  public void setPublisher(UUID publisher) {
    this.publisher = publisher;
  }

  @Override
  public String toString() {
    return "RdsConfigurationMessage [type=" + actionType
        + ", routingGroupId=" + routingGroupId
        + ", publisher=" + publisher
        + "]";
  }

}
