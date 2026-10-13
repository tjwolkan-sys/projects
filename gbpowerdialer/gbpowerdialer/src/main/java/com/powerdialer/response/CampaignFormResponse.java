package com.powerdialer.response;

import com.powerdialer.dto.CampaignFormDTO;

public class CampaignFormResponse {

  private CampaignFormDTO campaign;
  private boolean foreignKeyAdditionError;

  public CampaignFormResponse(CampaignFormDTO campaign, boolean foreignKeyAdditionError) {
    this.campaign = campaign;
    this.foreignKeyAdditionError = foreignKeyAdditionError;
  }

  public CampaignFormDTO getCampaign() {
    return campaign;
  }

  public void setCampaign(CampaignFormDTO campaign) {
    this.campaign = campaign;
  }

  public boolean isForeignKeyAdditionError() {
    return foreignKeyAdditionError;
  }

  public void setForeignKeyAdditionError(boolean foreignKeyAdditionError) {
    this.foreignKeyAdditionError = foreignKeyAdditionError;
  }
}
