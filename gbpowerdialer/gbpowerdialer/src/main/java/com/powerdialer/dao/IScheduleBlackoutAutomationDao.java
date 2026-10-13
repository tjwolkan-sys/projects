package com.powerdialer.dao;

import com.powerdialer.model.ScheduleBlackoutAutomation;
import java.util.List;

public interface IScheduleBlackoutAutomationDao extends IGenericDao<ScheduleBlackoutAutomation> {
  List<ScheduleBlackoutAutomation> loadScheduleBlackoutAutomationByCampaignId(Long campaignId);
}
