package com.powerdialer.dao;

import java.util.List;

import com.powerdialer.model.ResultCodeSchedule;

public interface IResultCodeScheduleDao extends IGenericDao<ResultCodeSchedule> {

  List<ResultCodeSchedule> getByCampaignId(Long campaignId);

}
