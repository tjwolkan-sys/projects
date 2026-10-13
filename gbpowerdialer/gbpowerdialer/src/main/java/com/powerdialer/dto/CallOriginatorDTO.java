package com.powerdialer.dto;

import java.util.List;

public class CallOriginatorDTO {

  private ServerDTO server;
  private List<CallDTO> callQueue;

  public ServerDTO getServer() {
    return server;
  }

  public void setServer(ServerDTO server) {
    this.server = server;
  }

  public List<CallDTO> getCallQueue() {
    return callQueue;
  }

  public void setCallQueue(List<CallDTO> callQueue) {
    this.callQueue = callQueue;
  }

}
