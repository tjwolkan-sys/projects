package com.powerdialer.dto;

import java.util.List;

public class CampaignContextDTO {

  private CampaignDTO campaign;
  private List<CallDTO> dataQueue;
  private List<CallDTO> callQueue;

  public CampaignDTO getCampaign() {
    return campaign;
  }

  public void setCampaign(CampaignDTO campaign) {
    this.campaign = campaign;
  }

  public List<CallDTO> getDataQueue() {
    return dataQueue;
  }

  public void setDataQueue(List<CallDTO> dataQueue) {
    this.dataQueue = dataQueue;
  }

  public List<CallDTO> getCallQueue() {
    return callQueue;
  }

  public void setCallQueue(List<CallDTO> callQueue) {
    this.callQueue = callQueue;
  }

}
