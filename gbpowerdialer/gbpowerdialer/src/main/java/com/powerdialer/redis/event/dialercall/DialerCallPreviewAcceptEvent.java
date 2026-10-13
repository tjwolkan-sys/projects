package com.powerdialer.redis.event.dialercall;

import com.powerdialer.redis.event.CallEvent;

import java.util.Date;

public class DialerCallPreviewAcceptEvent extends CallEvent {
  public DialerCallPreviewAcceptEvent(String publisher, String interactionId, Date createDate) {
    super(publisher, interactionId, createDate);
  }

  @Override
  public String toString() {
    return getEventClass() +  "{" +
        "createDate=" + getCreateDate() +
        ", publisher='" + getPublisher() + '\'' +
        ", interactionId='" + getInteractionId() + '\'' +
        '}';
  }
}
