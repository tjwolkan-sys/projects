package com.powerdialer.dao;

import java.util.List;

import com.powerdialer.model.Schedule;

public interface IScheduleDao extends IGenericDao<Schedule> {
  
  List<Schedule> loadScheduleByCampaign(Long campaignId);
  
}
