package com.powerdialer.redis.message;

public class UserMessage {

  private String publisher;
  private Long userId;

  public UserMessage() {
  }

  public UserMessage(Long userId) {
    this.userId = userId;
  }

  public String getPublisher() {
    return publisher;
  }

  public void setPublisher(String publisher) {
    this.publisher = publisher;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + " [publisher=" + publisher
        + ", userId=" + userId
        + "]";
  }
}
