package com.powerdialer.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

public class DNCPhoneNumberListDTO extends BaseEntityDTO {

  private String phoneNumber;
  private int reason;
  private Date createDate;
  private Date expiredDate;

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public int getReason() {
    return reason;
  }

  public void setReason(int reason) {
    this.reason = reason;
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER)
  public Date getCreateDate() {
    return createDate;
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER)
  public void setCreateDate(Date createDate) {
    this.createDate = createDate;
  }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER)
  public Date getExpiredDate() { return expiredDate; }

  @JsonFormat(shape = JsonFormat.Shape.NUMBER)
  public void setExpiredDate(Date expiredDate) { this.expiredDate = expiredDate; }
}
