package com.powerdialer.redis.event.dialercall;

import com.powerdialer.redis.event.CallEvent;

import java.util.Date;

public class DialerCallAgentAnswerEvent extends CallEvent {

  private final String username;

  public DialerCallAgentAnswerEvent(String publisher, String interactionId, Date createDate, String username) {
    super(publisher, interactionId, createDate);
    this.username = username;
  }

  public String getUsername() {
    return username;
  }

  @Override
  public String toString() {
    return getEventClass() +  "{" +
        "createDate=" + getCreateDate() +
        ", publisher='" + getPublisher() + '\'' +
        ", interactionId='" + getInteractionId() + '\'' +
        ", username='" + username + '\'' +
        '}';
  }

}
