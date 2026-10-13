package com.powerdialer.request;

/**
 * Request for adding new phone number columns to campaign's source table
 */
public class AddPhoneNumberColumnsRequest extends DbTableRequest {

  private String callerIdColumnName;
  private String phoneNumberColumnName;
  private String callFromTimeColumnName;
  private String callTillTimeColumnName;
  private String timezoneOffsetColumnName;

  public String getCallerIdColumnName() {
    return callerIdColumnName;
  }

  public void setCallerIdColumnName(String callerIdColumnName) {
    this.callerIdColumnName = callerIdColumnName;
  }

  public String getPhoneNumberColumnName() {
    return phoneNumberColumnName;
  }

  public void setPhoneNumberColumnName(String phoneNumberColumnName) {
    this.phoneNumberColumnName = phoneNumberColumnName;
  }

  public String getCallFromTimeColumnName() {
    return callFromTimeColumnName;
  }

  public void setCallFromTimeColumnName(String callFromTimeColumnName) {
    this.callFromTimeColumnName = callFromTimeColumnName;
  }

  public String getCallTillTimeColumnName() {
    return callTillTimeColumnName;
  }

  public void setCallTillTimeColumnName(String callTillTimeColumnName) {
    this.callTillTimeColumnName = callTillTimeColumnName;
  }

  public String getTimezoneOffsetColumnName() {
    return timezoneOffsetColumnName;
  }

  public void setTimezoneOffsetColumnName(String timezoneOffsetColumnName) {
    this.timezoneOffsetColumnName = timezoneOffsetColumnName;
  }

}
