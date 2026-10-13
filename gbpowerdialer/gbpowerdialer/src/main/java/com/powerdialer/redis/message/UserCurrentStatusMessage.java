package com.powerdialer.redis.message;

import com.powerdialer.model.Status;

public class UserCurrentStatusMessage extends UserMessage {

  private Status status;
  private Long statusSetDate;
  private boolean selectingResultCode;
  private boolean preOrderedStatusPresent;

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Long getStatusSetDate() {
    return statusSetDate;
  }

  public void setStatusSetDate(Long statusSetDate) {
    this.statusSetDate = statusSetDate;
  }

  public boolean isSelectingResultCode() {
    return selectingResultCode;
  }

  public void setSelectingResultCode(boolean selectingResultCode) {
    this.selectingResultCode = selectingResultCode;
  }

  public boolean isPreOrderedStatusPresent() {
    return preOrderedStatusPresent;
  }

  public void setPreOrderedStatusPresent(boolean preOrderedStatusPresent) {
    this.preOrderedStatusPresent = preOrderedStatusPresent;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + " [publisher=" + getPublisher()
        + ", userId=" + getUserId()
        + ", status=" + status
        + ", statusSetDate=" + statusSetDate
        + ", isSelectingResultCode=" + selectingResultCode
        + ", isPreOrderedStatusPresent=" + preOrderedStatusPresent
        + "]";
  }

}
