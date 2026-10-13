package com.powerdialer.validators;

public class ValidationResult {

  private boolean result;
  private String[] messages;

  public ValidationResult(boolean result,
                          String[] messages) {
    this.result = result;
    this.messages = messages;
  }

  public boolean getResult() {
    return result;
  }

  public String[] getMessages() {
    return messages;
  }

}
