package com.powerdialer.redis.event;

import java.util.Date;

public abstract class CallEvent {

  private Date createDate = new Date();
  private final String publisher;
  private final String interactionId;
  private final String eventClass;

  protected CallEvent(String publisher, String interactionId) {
    this.publisher = publisher;
    this.eventClass = this.getClass().getSimpleName();
    this.interactionId = interactionId;
  }

  protected CallEvent(String publisher, String interactionId, Date createDate) {
    this.publisher = publisher;
    this.eventClass = this.getClass().getSimpleName();
    this.interactionId = interactionId;
    this.createDate = createDate;
  }

  public void setCreateDate(Date createDate) {
    this.createDate = createDate;
  }

  public Date getCreateDate() {
    return createDate;
  }

  public String getPublisher() {
    return publisher;
  }

  public String getInteractionId() {
    return interactionId;
  }

  public String getEventClass() {
    return eventClass;
  }

  @Override
  public String toString() {
    return eventClass +  "{" +
        "createDate=" + createDate +
        ", publisher='" + publisher + '\'' +
        ", interactionId='" + interactionId + '\'' +
        '}';
  }
}
