package com.powerdialer.dto;

/**
 * Monitor ekranındaki canlı aktivite feed'i için tek olay kaydı. {@code id} alanı AngularJS
 * {@code track by} anahtarıdır; feed satırlarının yeniden çizilmemesini sağlar.
 */
public class FctChannelEventDTO {

  private long id;
  private long ts;
  private String kind;
  private String phoneNumber;
  private String callId;
  private String detail;

  public FctChannelEventDTO() {
  }

  public FctChannelEventDTO(long id, long ts, String kind, String phoneNumber, String callId, String detail) {
    this.id = id;
    this.ts = ts;
    this.kind = kind;
    this.phoneNumber = phoneNumber;
    this.callId = callId;
    this.detail = detail;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getTs() {
    return ts;
  }

  public void setTs(long ts) {
    this.ts = ts;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public String getCallId() {
    return callId;
  }

  public void setCallId(String callId) {
    this.callId = callId;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }
}
