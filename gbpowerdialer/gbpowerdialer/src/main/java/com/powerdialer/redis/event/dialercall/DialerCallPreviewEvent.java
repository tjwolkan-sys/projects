package com.powerdialer.redis.event.dialercall;

import com.powerdialer.redis.event.CallEvent;

import java.util.Date;

public class DialerCallPreviewEvent extends CallEvent {

  private final Long campaignId;
  private final String dnis;
  private final String username;
  private final Date fetchDate;
  private final String serverName;
  private final String callerId;

  public DialerCallPreviewEvent(String publisher, String interactionId, Long campaignId, String dnis, String username, String serverName, String callerId, Date fetchDate) {
    super(publisher, interactionId);
    this.campaignId = campaignId;
    this.dnis = dnis;
    this.username = username;
    this.fetchDate = fetchDate;
    this.serverName = serverName;
    this.callerId = callerId;
  }

  public String getDnis() {
    return dnis;
  }

  public Long getCampaignId() {
    return campaignId;
  }

  public String getUsername() {
    return username;
  }

  public Date getFetchDate() {
    return fetchDate;
  }

  public String getServerName() {
    return serverName;
  }

  public String getCallerId() {
    return callerId;
  }

  @Override
  public String toString() {
    return "DialerCallPreviewEvent{" +
            "createDate=" + getCreateDate() +
            ", publisher='" + getPublisher() + '\'' +
            ", interactionId='" + getInteractionId() + '\'' +
            ", campaignId=" + campaignId +
            ", dnis='" + dnis + '\'' +
            ", username='" + username + '\'' +
            ", fetchDate=" + fetchDate +
            ", serverName='" + serverName + '\'' +
            ", callerId='" + callerId + '\'' +
            '}';
  }
}
