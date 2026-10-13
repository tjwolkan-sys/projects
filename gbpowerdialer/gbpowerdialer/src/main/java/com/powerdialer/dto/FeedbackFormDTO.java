package com.powerdialer.dto;

import com.powerdialer.util.Constants.FEEDBACK_TYPE;

public class FeedbackFormDTO extends FeedbackListDTO {

  private String message;
  private FEEDBACK_TYPE type;
  private String logArray;

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public FEEDBACK_TYPE getType() {
    return type;
  }

  public void setType(FEEDBACK_TYPE type) {
    this.type = type;
  }

  public String getLogArray() {
    return logArray;
  }

  public void setLogArray(String logArray) {
    this.logArray = logArray;
  }

}
