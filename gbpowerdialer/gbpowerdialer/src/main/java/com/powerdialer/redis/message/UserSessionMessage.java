package com.powerdialer.redis.message;

import com.powerdialer.model.User;

public class UserSessionMessage extends UserMessage {

  public enum SESSION_MESSAGE_TYPE {
    LOGIN,
    LOGOUT
  }

  private SESSION_MESSAGE_TYPE type;
  private User user;

  public SESSION_MESSAGE_TYPE getType() {
    return type;
  }

  public void setType(SESSION_MESSAGE_TYPE type) {
    this.type = type;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + " [publisher=" + getPublisher()
        + ", userId=" + getUserId()
        + ", type=" + type
        + ", user=" + user
        + "]";
  }

}
