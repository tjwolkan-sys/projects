package com.powerdialer.dao;

import java.util.List;

import com.powerdialer.model.ScheduleAutomation;

public interface IScheduleAutomationDao extends IGenericDao<ScheduleAutomation> {
  List<ScheduleAutomation> loadScheduleAutomationByCampaignId(Long campaignId);
}
