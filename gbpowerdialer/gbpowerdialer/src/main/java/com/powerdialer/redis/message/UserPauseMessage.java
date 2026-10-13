package com.powerdialer.redis.message;

public class UserPauseMessage extends UserMessage {

  private boolean paused;

  public UserPauseMessage() {}

  public UserPauseMessage(Long userId, boolean paused) {
    super(userId);
    this.paused = paused;
  }

  public boolean isPaused() {
    return paused;
  }

  public void setPaused(boolean paused) {
    this.paused = paused;
  }


  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "[publisher=" + getPublisher()
        + ", userId=" + getUserId()
        + ", paused=" + paused
        + "]";
  }
}
