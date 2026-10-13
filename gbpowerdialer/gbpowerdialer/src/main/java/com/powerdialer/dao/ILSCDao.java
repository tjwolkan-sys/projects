package com.powerdialer.dao;

import java.util.List;

import com.powerdialer.model.LSC;

public interface ILSCDao extends IGenericDao<LSC> {
  List<LSC> getLscsForCampaign(Long campaignId, boolean enabled);

  List<LSC> getGlobalLscs(boolean enabled);

  LSC getWithInitializedCampaigns(long id);

  List<LSC> getLSCsByCampaignId(Long campaignId);
}
