package com.powerdialer.dao;

import java.util.List;

import com.powerdialer.model.PredefinedValue;

public interface IPredefinedValueDao extends IGenericDao<PredefinedValue> {

  List<PredefinedValue> getPredefinedValuesByCampaignId(Long campaignId);

}
