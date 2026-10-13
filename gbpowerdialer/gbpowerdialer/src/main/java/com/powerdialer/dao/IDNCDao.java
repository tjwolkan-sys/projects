package com.powerdialer.dao;

import java.util.List;

import org.json.JSONObject;

import com.powerdialer.model.DNC;
import com.powerdialer.util.ListDetailed;

public interface IDNCDao extends IGenericDao<DNC> {

  List<DNC> getDNCForCampaign(Long campaignId, boolean enabled);

  List<DNC> getGlobalDNC(boolean enabled);

  DNC getDNCByDNCPhoneNumberId(Long dncPhoneNumberId);

  DNC getWithInitializedPhoneNumbers(Long id);

  DNC getDefaultDNCByCampaignId(Long campaignId);

  DNC getDefaultDNCByCampaignName(String campaignName);

  long getTenantDNCCount(Long tenantId, boolean isCountGlobal, boolean includeDisabled, Long... excludedIds);
  @SuppressWarnings("unchecked")
  ListDetailed<DNC> getDNCListsByCampaignsIds(JSONObject page, JSONObject sort, JSONObject filters, Long[] campaignIds, boolean all);

  List<DNC> getDNCListsByIds(Long[] dncIds);

  DNC getTenantDNC(Long tenantId, boolean isCountGlobal, boolean includeDisabled);

  List<DNC> getDNCsByCampaignId(Long campaignId);

}
