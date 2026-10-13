package com.powerdialer.engine.asterisk.actions;

import org.asteriskjava.manager.action.OriginateAction;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Server;
import com.powerdialer.serialization.OriginateActionDataContainerSerializer;

/**
 * OriginateAction's wrapper for preview campaign
 */
@JsonSerialize(using = OriginateActionDataContainerSerializer.class)
public class OriginateActionDataContainer {

  private OriginateAction originateAction;
  private Call call;
  private Campaign campaign;
  private Server server;

  public OriginateActionDataContainer(OriginateAction originateAction,
                                      Call call,
                                      Campaign campaign,
                                      Server server) {
    this.originateAction = originateAction;
    this.call = call;
    this.campaign = campaign;
    this.server = server;
  }

  public OriginateAction getOriginateAction() {
    return originateAction;
  }

  public Call getCall() {
    return call;
  }

  public Campaign getCampaign() {
    return campaign;
  }

  public Server getServer() {
    return server;
  }

}
