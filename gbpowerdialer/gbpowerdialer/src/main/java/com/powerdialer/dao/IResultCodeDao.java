package com.powerdialer.dao;

import java.util.List;

import com.powerdialer.model.ResultCode;

public interface IResultCodeDao extends IGenericDao<ResultCode> {

  List<ResultCode> getResultCodesByCampaignId(Long id, boolean nonFinalOnly);

  List<ResultCode> getResultCodesByGroupIds(Long[] groupIds, boolean nonFinalOnly);
  
}
