package com.powerdialer.dto;

/**
 * Bir FCT numarası üzerindeki tek kanal rezervasyonu. Müşteri verisi taşımaz; yalnız dialer
 * interaction id'si ve rezervasyon zamanı bulunur.
 */
public class FctInFlightDTO {

  private String callId;
  private long sinceMs;
  private Long campaignId;

  public FctInFlightDTO() {
  }

  public FctInFlightDTO(String callId, long sinceMs, Long campaignId) {
    this.callId = callId;
    this.sinceMs = sinceMs;
    this.campaignId = campaignId;
  }

  public String getCallId() {
    return callId;
  }

  public void setCallId(String callId) {
    this.callId = callId;
  }

  public long getSinceMs() {
    return sinceMs;
  }

  public void setSinceMs(long sinceMs) {
    this.sinceMs = sinceMs;
  }

  public Long getCampaignId() {
    return campaignId;
  }

  public void setCampaignId(Long campaignId) {
    this.campaignId = campaignId;
  }
}
