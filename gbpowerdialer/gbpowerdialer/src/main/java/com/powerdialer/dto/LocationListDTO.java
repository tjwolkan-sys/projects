package com.powerdialer.dto;

import java.util.List;

import org.dozer.Mapping;

public class LocationListDTO extends BaseEntityTenantDTO {

  @Mapping("servers")
  private List<ServerListDTO> serverListDTOs;

  @Mapping("campaigns")
  private List<CampaignListDTO> campaignListDTOs;

  public List<ServerListDTO> getServerListDTOs() {
    return serverListDTOs;
  }

  public void setServerListDTOs(List<ServerListDTO> serverListDTOs) {
    this.serverListDTOs = serverListDTOs;
  }

  public List<CampaignListDTO> getCampaignListDTOs() {
    return campaignListDTOs;
  }

  public void setCampaignListDTOs(List<CampaignListDTO> campaignListDTOs) {
    this.campaignListDTOs = campaignListDTOs;
  }
}
