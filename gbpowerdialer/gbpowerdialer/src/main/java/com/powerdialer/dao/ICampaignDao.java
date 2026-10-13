package com.powerdialer.dao;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.powerdialer.model.Campaign;
import com.powerdialer.model.Tenant;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;

public interface ICampaignDao extends IGenericDao<Campaign> {

  void updateStoppedDate(Long campaignId, Date stoppedDate);

  List<Campaign> getByName(String name, boolean deleted);

  List<Long> getCampaignIdsCreatedByUser(User user, boolean deleted);

  List<Long> getCampaignIdsCreatedByTenants(Collection<Long> tenantsIds, boolean deleted);

  List<Campaign> getAll(Boolean enabled, Boolean deleted, Long[] ids);

  List<Campaign> getAll(Boolean deleted);

  void updateStopType(Long campaignId, Constants.CAMPAIGN_STOP_TYPE stopType);

  void updateLscPriority(Long campaignId, Long lscId, int priority);

  void resetLscPriorities(Long campaignId);

  //void lockCampaignForUpdate(Long campaignId);
}
