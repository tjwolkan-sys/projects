package com.powerdialer.redis.event.dialercall;

import com.powerdialer.redis.event.CallEvent;

import java.util.Date;

public class DialerCallDialEvent extends CallEvent {

  private final Long campaignId;
  private final String dnis;
  private final Long ivrId;
  private final String serverName;
  private final String callerId;
  private final Date fetchDate;

  public DialerCallDialEvent(String publisher, String interactionId, Long campaignId, Date date, String dnis, Long ivrId, String serverName, String callerId, Date fetchDate) {
    super(publisher, interactionId);
    this.campaignId = campaignId;
    this.fetchDate = fetchDate;
    this.setCreateDate(date);
    this.dnis = dnis;
    this.ivrId = ivrId;
    this.serverName = serverName;
    this.callerId = callerId;
  }

  public String getDnis() {
    return dnis;
  }

  public Long getCampaignId() {
    return campaignId;
  }

  public Long getIvrId() {
    return ivrId;
  }

  public String getServerName() {
    return serverName;
  }

  public Date getFetchDate() {
    return fetchDate;
  }

  public String getCallerId() {
    return callerId;
  }

    @Override
    public String toString() {
        return "DialerCallDialEvent{" +
                "campaignId=" + campaignId +
                ", dnis='" + dnis + '\'' +
                ", ivrId=" + ivrId +
                ", serverName='" + serverName + '\'' +
                ", callerId='" + callerId + '\'' +
                ", fetchDate=" + fetchDate +
                "} " + super.toString();
    }
}
