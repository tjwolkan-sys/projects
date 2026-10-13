package com.powerdialer.dao;

import com.powerdialer.model.CampaignMemento;

public interface ICampaignMementoDao extends IGenericDao<CampaignMemento> {

  CampaignMemento getCampaignMementoByCampaignId(Long campaignId);

  void resetCampaignMementoByCampaignId(Long campaignId);

}
